# Registration Flow Fix - Summary

## Problem Fixed
**Before:** Email was saved to database during registration, before OTP verification. If OTP verification failed or user abandoned the process, the email remained registered, blocking new registration attempts with the same email.

**After:** Email is saved to database **only after successful OTP verification**. If OTP verification fails or expires, the registration data is automatically discarded.

## Solution Implemented

### Two-Phase Registration Process

#### Phase 1: Temporary Storage (Registration)
```
User submits registration form
    ↓
System validates input
    ↓
OTP is generated
    ↓
Registration data stored in CACHE (PendingRegistrationService)
    ↓
OTP sent to email
    ↓
User sees OTP verification page
    ↓
User data NOT in database yet ✓
```

#### Phase 2: Database Save (OTP Verification)
```
User enters OTP
    ↓
If OTP is VALID:
    ├─ Temporary data moved to database
    ├─ User marked as emailVerified = true
    ├─ Wallet created
    ├─ Welcome email sent
    ├─ Temporary data removed from cache
    ├─ User can login ✓
    ↓
If OTP is INVALID:
    ├─ User sees error
    ├─ Can resend OTP
    ├─ Data stays in cache (can retry)
    ↓
If OTP expires (10 min):
    ├─ Auto-cleanup removes cache entry
    ├─ User can register again ✓
```

## New Components Created

### 1. **PendingRegistration.java** (Model)
Holds temporary registration data:
- Email, name, branch, division, roll number
- Phone, batch, encoded password
- OTP and expiry time

### 2. **PendingRegistrationService.java** (Service)
Manages pending registrations in cache:
- Thread-safe ConcurrentHashMap storage
- Auto-cleanup every 5 minutes
- Methods: save, get, verify, resend, remove

## Updated Components

### **UserService.java**
Three methods updated:

1. **registerUser()**
   - ✓ Check email not in DB and not pending
   - ✓ Validate input
   - ✓ Create PendingRegistration
   - ✓ Store in cache (NOT database)
   - ✓ Send OTP
   - ✗ NO database save yet

2. **verifyOtp()**
   - ✓ Check pending registrations first
   - ✓ Verify OTP against pending data
   - ✓ If valid: Create User from pending → Save to DB
   - ✓ Create wallet, send welcome email
   - ✓ Remove from pending cache
   - ✓ Return true on success

3. **resendOtp()**
   - ✓ Generate new OTP
   - ✓ Update pending registration
   - ✓ Send OTP email
   - ✓ Works for both pending and registered users

## Key Features

✓ **No Email Conflicts**: Email only in database after verification
✓ **Automatic Cleanup**: Expired registrations removed after 10 minutes
✓ **Thread-Safe**: Uses ConcurrentHashMap
✓ **Backward Compatible**: Works with existing users
✓ **No Database Schema Changes**: Uses existing columns
✓ **No Frontend Changes**: Existing HTML forms work as-is
✓ **Self-Healing**: Auto-cleanup prevents stale data

## Configuration

- **OTP Validity**: 10 minutes (change in `registerUser()` method)
- **Cleanup Interval**: 5 minutes (change in `PendingRegistrationService`)
- **Storage**: In-memory cache (ConcurrentHashMap)

## Testing Scenarios

| Scenario | Before | After |
|----------|--------|-------|
| Register + Don't Verify | ❌ Email locked forever | ✓ Can retry after 10 min |
| Register + Verify OTP | ✓ Works | ✓ Works (same) |
| Wrong OTP | ✓ Show error | ✓ Show error (can retry) |
| Duplicate Email | ❌ "Email exists" error | ✓ "Registration in progress" (helpful) |
| Manual Resend | ✓ Works | ✓ Works (same) |

## Files Changed

### Created (NEW)
- `src/main/java/com/pdfprinting/model/PendingRegistration.java`
- `src/main/java/com/pdfprinting/service/PendingRegistrationService.java`

### Modified
- `src/main/java/com/pdfprinting/service/UserService.java`
  - Injected PendingRegistrationService
  - Updated registerUser() method
  - Updated verifyOtp() method
  - Updated resendOtp() method
  - Added createUserFromPendingRegistration() helper

### No Changes (Backward Compatible)
- All controller files
- All HTML templates
- All other service files
- Database schema

## How It Works - Step by Step

### Step 1: User Registration
```
POST /register with form data
    ↓
UserService.registerUser(user):
  1. Check email not in DB and not pending
  2. Validate branch/division/rollNumber
  3. Encode password
  4. Generate OTP (6-digit)
  5. Create PendingRegistration object
  6. PendingRegistrationService.save(pending)  ← Stored in cache only
  7. EmailService.sendOtpEmail()
  8. Return user object (not in DB)
    ↓
AuthController redirects to /verify-otp?email=...
    ↓
User checks email for OTP
```

### Step 2: OTP Verification
```
POST /verify-otp with email and OTP code
    ↓
UserService.verifyOtp(email, otp):
  1. Check PendingRegistrationService.hasPendingRegistration(email)
  2. If found:
     a. Verify OTP with PendingRegistrationService
     b. If OTP valid:
        i.   Create User from PendingRegistration
        ii.  userRepository.save(user)  ← NOW saved to DB!
        iii. Set emailVerified = true
        iv.  WalletService.createWallet()
        v.   EmailService.sendWelcomeEmail()
        vi.  PendingRegistrationService.remove()
        vii. Return true
     c. If OTP invalid:
        i.  Return false
  3. If not found:
     - Check existing database users (backward compatibility)
    ↓
AuthController redirects to dashboard or shows error
    ↓
User is now registered and verified! ✓
```

### Step 3: Background Cleanup (Every 5 minutes)
```
ScheduledExecutor runs cleanupExpiredRegistrations()
    ↓
For each pending registration:
  1. Check if otpExpiry is before now()
  2. Remove if expired
  3. Log cleanup results
    ↓
Prevents stale data in memory cache
```

## User Experience Flow

### Success Path ✓
```
1. Fill registration form
2. Click Register
3. See "OTP sent to your email"
4. Receive OTP in inbox
5. Enter OTP
6. Click Verify
7. See "Registration successful"
8. Redirected to dashboard
```

### Retry Path (Wrong OTP)
```
1. Fill registration form
2. Click Register
3. Receive OTP
4. Enter WRONG OTP
5. See "Invalid OTP. Please try again"
6. Re-enter correct OTP
7. See "Registration successful"
```

### Resend Path (Didn't receive OTP)
```
1. Fill registration form
2. Click Register
3. Check email (not received)
4. Click "Resend OTP"
5. Receive new OTP
6. Enter OTP
7. See "Registration successful"
```

### Timeout Path (Expired OTP)
```
1. Fill registration form
2. Click Register
3. Receive OTP
4. Wait 11+ minutes (expired)
5. Try to verify OTP
6. See "OTP expired"
7. Click "Register Again"
8. Start new registration with same email ✓ (works now!)
```

## Migration Guide

**No migration needed!** This is a backward-compatible change:

1. Existing verified users: No changes, work as before
2. Existing unverified users in DB: Still work with fallback logic
3. New registrations: Use new two-phase process
4. No database schema changes required

## Performance Impact

- **Minimal**: Uses in-memory cache (fast access)
- **Cleanup overhead**: Low (runs every 5 minutes)
- **Memory usage**: Minimal (stores OTP registration data temporarily)

Typical memory per pending registration: < 1 KB
Example: 1000 pending registrations ≈ 1 MB

## Security Considerations

✓ Passwords are always encoded before storage (both cache and DB)
✓ OTP is 6-digit random number (1 in 1 million chance)
✓ OTP expires after 10 minutes (can resend)
✓ Email verification required before account activation
✓ Pending registrations in cache, not exposing data in DB
✓ Thread-safe implementation (ConcurrentHashMap)

## Monitoring & Debugging

### Debug Logs
Look for `[DEBUG]` logs in console:
- "Saved pending registration for email: ..."
- "OTP verification for ... : storedOtp=..."
- "User successfully registered and verified: ..."
- "Cleaned up X expired pending registrations"

### Check Pending Registrations Count
You can add a debug endpoint:
```java
@GetMapping("/debug/pending-count")
@ResponseBody
public int getPendingCount() {
    return pendingRegistrationService.getPendingRegistrationCount();
}
```

## Next Steps

1. ✓ Code implemented and compiled
2. → Test in development environment
3. → Test each scenario from Testing Checklist
4. → Deploy to production
5. → Monitor logs for any issues

## Support & Troubleshooting

**Q: User getting "Registration already in progress" error**
A: Previous registration within 10 minutes. User should verify OTP or wait 10 minutes.

**Q: User not found during OTP verification**
A: Pending registration expired. User must register again.

**Q: Email is showing in database but user didn't verify**
A: This shouldn't happen with new code. Check application logs for errors.

---

## Summary
The new registration flow ensures **email addresses are saved to the database only after successful OTP verification**, eliminating duplicate email conflicts and providing a cleaner user experience. The system automatically cleans up unverified registrations after 10 minutes, allowing users to retry if needed.
