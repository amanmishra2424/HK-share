package com.pdfprinting.service;

import com.pdfprinting.model.RefundRequest;
import com.pdfprinting.model.Transaction;
import com.pdfprinting.model.User;
import com.pdfprinting.model.Wallet;
import com.pdfprinting.repository.RefundRequestRepository;
import com.pdfprinting.repository.TransactionRepository;
import com.pdfprinting.repository.WalletRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class RefundService {

    private static final BigDecimal REFUND_FEE_PERCENT = new BigDecimal("2.00"); // 2%

    @Autowired
    private RefundRequestRepository refundRequestRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private WalletService walletService;

    public List<RefundRequest> listPending() {
        return refundRequestRepository.findByStatusOrderByCreatedAtDesc(RefundRequest.Status.PENDING);
    }

    public List<RefundRequest> listByUser(User user) {
        return refundRequestRepository.findByUserOrderByCreatedAtDesc(user);
    }

    @Transactional
    public RefundRequest createRequest(User user, BigDecimal requestedAmount, String upiId, String reason) throws Exception {
        Wallet wallet = walletService.getOrCreateWallet(user);
        // Prevent duplicate PENDING requests
        if (refundRequestRepository.existsByUserAndStatus(user, RefundRequest.Status.PENDING)) {
            throw new Exception("You already have a pending refund request. Please wait for processing (up to 7 working days).");
        }
        if (requestedAmount == null || requestedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new Exception("Invalid amount");
        }
        if (wallet.getBalance().compareTo(requestedAmount) < 0) {
            throw new Exception("Requested amount exceeds wallet balance");
        }
        // Compute fee and net payout
        BigDecimal feeAmount = requestedAmount.multiply(REFUND_FEE_PERCENT).divide(new BigDecimal("100"));
        // Round to 2 decimals
        feeAmount = feeAmount.setScale(2, BigDecimal.ROUND_HALF_UP);
        BigDecimal netPayout = requestedAmount.subtract(feeAmount);
        if (netPayout.compareTo(BigDecimal.ZERO) <= 0) {
            throw new Exception("Net payout must be positive after fees");
        }

        RefundRequest rr = new RefundRequest();
        rr.setUser(user);
        rr.setAmountRequested(requestedAmount.setScale(2, BigDecimal.ROUND_HALF_UP));
        rr.setFeePercent(REFUND_FEE_PERCENT);
        rr.setFeeAmount(feeAmount);
        rr.setNetPayout(netPayout);
        rr.setUpiId(upiId);
        rr.setReason(reason);
        rr.setStatus(RefundRequest.Status.PENDING);
        // nothing to deduct now; wallet is deducted only after admin approval
        return refundRequestRepository.save(rr);
    }

    @Transactional
    public RefundRequest approveAndProcess(Long requestId, String payoutReference, String adminNote) throws Exception {
        Optional<RefundRequest> opt = refundRequestRepository.findById(requestId);
        if (opt.isEmpty()) throw new Exception("Refund request not found");
        RefundRequest rr = opt.get();
        if (rr.getStatus() != RefundRequest.Status.PENDING) throw new Exception("Refund not in PENDING state");

        User user = rr.getUser();
        Wallet wallet = walletService.getOrCreateWallet(user);

        // Ensure wallet still has sufficient balance when processing
        if (wallet.getBalance().compareTo(rr.getAmountRequested()) < 0) {
            throw new Exception("Insufficient wallet balance at processing time");
        }

        // Deduct from wallet and record a single transaction (as deduction) with payout reference
        boolean deducted = walletService.withdrawMoney(user, rr.getAmountRequested(), "Wallet withdrawal for refund request #" + rr.getId(), payoutReference);
        if (!deducted) {
            throw new Exception("Failed to deduct wallet balance");
        }

        rr.setStatus(RefundRequest.Status.PROCESSED);
        rr.setPayoutReference(payoutReference);
        rr.setAdminNote(adminNote);
        rr.setProcessedAt(LocalDateTime.now());
        return refundRequestRepository.save(rr);
    }

    @Transactional
    public RefundRequest reject(Long requestId, String adminNote) throws Exception {
        Optional<RefundRequest> opt = refundRequestRepository.findById(requestId);
        if (opt.isEmpty()) throw new Exception("Refund request not found");
        RefundRequest rr = opt.get();
        if (rr.getStatus() != RefundRequest.Status.PENDING) throw new Exception("Refund not in PENDING state");
        rr.setStatus(RefundRequest.Status.REJECTED);
        rr.setAdminNote(adminNote);
        rr.setProcessedAt(LocalDateTime.now());
        return refundRequestRepository.save(rr);
    }
}
