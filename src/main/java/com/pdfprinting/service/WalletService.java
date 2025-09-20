package com.pdfprinting.service;

import com.pdfprinting.model.Transaction;
import com.pdfprinting.model.User;
import com.pdfprinting.model.Wallet;
import com.pdfprinting.repository.TransactionRepository;
import com.pdfprinting.repository.WalletRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class WalletService {

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    // Price per page
    private static final BigDecimal PRICE_PER_PAGE = new BigDecimal("2.00");

    public Wallet getOrCreateWallet(User user) {
        Optional<Wallet> walletOpt = walletRepository.findByUser(user);
        if (walletOpt.isPresent()) {
            return walletOpt.get();
        }
        
        // Create new wallet for user
        Wallet wallet = new Wallet(user);
        return walletRepository.save(wallet);
    }

    public BigDecimal getWalletBalance(User user) {
        Wallet wallet = getOrCreateWallet(user);
        return wallet.getBalance();
    }

    @Transactional
    public boolean addMoney(User user, BigDecimal amount, String referenceId, String description) {
        try {
            Wallet wallet = getOrCreateWallet(user);
            wallet.addAmount(amount);
            walletRepository.save(wallet);

            // Create transaction record
            Transaction transaction = new Transaction(
                user, 
                Transaction.TransactionType.WALLET_TOPUP, 
                amount, 
                wallet.getBalance(), 
                description != null ? description : "Wallet top-up"
            );
            transaction.setReferenceId(referenceId);
            transactionRepository.save(transaction);

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Transactional
    public boolean deductMoney(User user, BigDecimal amount, String description) {
        try {
            Wallet wallet = getOrCreateWallet(user);
            
            if (wallet.deductAmount(amount)) {
                walletRepository.save(wallet);

                // Create transaction record
                Transaction transaction = new Transaction(
                    user, 
                    Transaction.TransactionType.PDF_BILLING, 
                    amount.negate(), // Negative amount for deduction
                    wallet.getBalance(), 
                    description != null ? description : "PDF upload billing"
                );
                transactionRepository.save(transaction);

                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @Transactional
    public boolean refundMoney(User user, BigDecimal amount, String description) {
        try {
            Wallet wallet = getOrCreateWallet(user);
            wallet.addAmount(amount);
            walletRepository.save(wallet);

            // Create transaction record
            Transaction transaction = new Transaction(
                user, 
                Transaction.TransactionType.REFUND, 
                amount, 
                wallet.getBalance(), 
                description != null ? description : "PDF deletion refund"
            );
            transactionRepository.save(transaction);

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public BigDecimal calculateCost(int pageCount, int copyCount) {
        return PRICE_PER_PAGE.multiply(new BigDecimal(pageCount))
                             .multiply(new BigDecimal(copyCount));
    }

    public boolean hasAmountRequired(User user, BigDecimal amount) {
        Wallet wallet = getOrCreateWallet(user);
        return wallet.hasAmount(amount);
    }

    public List<Transaction> getTransactionHistory(User user) {
        return transactionRepository.findByUserOrderByCreatedAtDesc(user);
    }

    public List<Transaction> getRecentTransactions(User user, int limit) {
        return transactionRepository.findTop20ByUserOrderByCreatedAtDesc(user);
    }

    public List<Transaction> getAllTransactions(User user) {
        return transactionRepository.findByUserOrderByCreatedAtDesc(user);
    }
}