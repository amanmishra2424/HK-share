package com.pdfprinting.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pdfprinting.model.PdfUpload.PrintType;
import com.pdfprinting.model.Transaction;
import com.pdfprinting.model.User;
import com.pdfprinting.model.Wallet;
import com.pdfprinting.repository.TransactionRepository;
import com.pdfprinting.repository.WalletRepository;

@Service
public class WalletService {

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    // Price per page for different print types
    private static final BigDecimal PRICE_SINGLE_SIDE = new BigDecimal("2.00");   // ₹2 per page (B&W single side)
    private static final BigDecimal PRICE_DOUBLE_SIDE = new BigDecimal("1.00");   // ₹1 per page (duplex)
    private static final BigDecimal PRICE_COLOUR = new BigDecimal("7.00");        // ₹7 per page (colour single side)

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

    /**
     * Withdraw money from wallet for manual refund/payouts (recorded as a deduction).
     * Uses existing PDF_BILLING type to avoid DB enum compatibility issues; description/reference clarify it's a withdrawal.
     */
    @Transactional
    public boolean withdrawMoney(User user, BigDecimal amount, String description, String referenceId) {
        try {
            Wallet wallet = getOrCreateWallet(user);
            if (wallet.deductAmount(amount)) {
                walletRepository.save(wallet);

                Transaction transaction = new Transaction(
                        user,
                        Transaction.TransactionType.PDF_BILLING, // treat as deduction; description marks as withdrawal
                        amount.negate(),
                        wallet.getBalance(),
                        description != null ? description : "Wallet withdrawal"
                );
                transaction.setReferenceId(referenceId);
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
        // Legacy method - uses single side pricing
        return PRICE_SINGLE_SIDE.multiply(new BigDecimal(pageCount))
                             .multiply(new BigDecimal(copyCount));
    }
    
    /**
     * Calculate cost based on print type
     * - SINGLE_SIDE: ₹2 per page
     * - DOUBLE_SIDE: ₹1 per page (odd pages rounded up to even)
     * - COLOUR: ₹7 per page
     */
    public BigDecimal calculateCost(int pageCount, int copyCount, PrintType printType) {
        BigDecimal pricePerPage;
        int billedPages = pageCount;
        
        switch (printType) {
            case DOUBLE_SIDE:
                // For duplex: if odd pages, add 1 blank page to make even
                if (pageCount % 2 != 0) {
                    billedPages = pageCount + 1;
                }
                pricePerPage = PRICE_DOUBLE_SIDE;
                break;
            case COLOUR:
                pricePerPage = PRICE_COLOUR;
                break;
            case SINGLE_SIDE:
            default:
                pricePerPage = PRICE_SINGLE_SIDE;
                break;
        }
        
        return pricePerPage.multiply(new BigDecimal(billedPages))
                          .multiply(new BigDecimal(copyCount));
    }
    
    /**
     * Calculate billed page count for a print type
     * For duplex, rounds odd pages up to even
     */
    public int calculateBilledPages(int pageCount, PrintType printType) {
        if (printType == PrintType.DOUBLE_SIDE && pageCount % 2 != 0) {
            return pageCount + 1;
        }
        return pageCount;
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