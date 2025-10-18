# Email Registration Flow - Implementation Guide

## Overview
This document describes the changes made to fix the registration flow so that email addresses are only saved to the database after successful OTP verification.

## Problem Statement
Previously, during user registration:
1. User enters registration details and clicks "Register"
2. **User data (including email) was immediately saved to the database**
3. OTP was generated and sent to the user's email
4. If user didn't verify the OTP or their session expired, the email remained in the database
5. When trying to register again with the same email, the system showed "Email already exists" error

## Solution
The new implementation uses a **two-phase registration process**:

### Phase 1: Temporary Registration (No Database Save)
- User submits registration form
- System validates input and checks for conflicts
- **User data is stored in cache (NOT in database)**
- OTP is generated and sent to email
- User is redirected to OTP verification page

### Phase 2: Database Save (After OTP Verification)
- User enters the OTP they received
- System verifies the OTP
- **Only if OTP is valid, user data is saved to the database**
- Wallet is created for the user
- Welcome email is sent
- User is redirected to dashboard/login

### If Verification Fails or Expires
- Temporary registration data is automatically removed from cache after 10 minutes
- User can register again with the same email
- No database records are created for failed registrations

## Implementation Details

### 1. New Model: `PendingRegistration.java`
Location: `src/main/java/com/pdfprinting/model/PendingRegistration.java`

Stores temporary registration data:
```java
- email
- name
- branch
- division
- rollNumber
- phoneNumber
- batch
- password (already encoded)
- otp
- otpExpiry
- createdAt
```

**Key Method**: `isExpired()` - Checks if OTP has expired (valid for 10 minutes)

### 2. New Service: `PendingRegistrationService.java`
Location: `src/main/java/com/pdfprinting/service/PendingRegistrationService.java`

Manages temporary registrations in a thread-safe cache:

**Key Methods:**
- `savePendingRegistration()` - Store temporary registration in cache
- `getPendingRegistration()` - Retrieve pending registration by email
- `hasPendingRegistration()` - Check if email has active pending registration
- `verifyOtpForPendingRegistration()` - Verify OTP against pending registration
- `resendOtpForPendingRegistration()` - Generate and store new OTP
- `removePendingRegistration()` - Clean up after successful verification
- `cleanupExpiredRegistrations()` - Auto-cleanup (runs every 5 minutes)

**Storage**: Uses `ConcurrentHashMap` for thread-safe storage in memory

### 3. Updated: `UserService.java`
Key changes:

#### `registerUser()` Method
**Before:**
- Saved user to database immediately
- Generated OTP
- Sent OTP email
- Returned saved user object

**After:**
- Validates input and checks for conflicts (same as before)
- ~~Does NOT save to database~~
- Creates `PendingRegistration` object
- Stores in cache using `PendingRegistrationService`
- Sends OTP email
- Returns unsaved user object (for controller use only)

```java
// Key changes:
1. Check if email already exists (DB and pending registrations)
2. Encode password
3. Generate OTP
4. Create PendingRegistration with OTP
5. Save to cache (NOT database)
6. Send OTP email
7. Return user object (not persisted)
```

#### `verifyOtp()` Method
**Before:**
- Found user in database
- Checked OTP validity
- Marked as verified
- Sent welcome email

**After:**
- First checks pending registrations
- If found and OTP is valid:
  1. Creates `User` entity from `PendingRegistration`
  2. Saves to database
  3. Creates wallet
  4. Sends welcome email
  5. Removes from pending registrations
- Falls back to checking already-registered users (backward compatibility)

```java
// Key flow:
if (pendingRegistrationService.hasPendingRegistration(email)) {
    if (verifyOtpForPendingRegistration(email, otp)) {
        // Create User from PendingRegistration
        User user = createUserFromPendingRegistration(pending);
        // Save to database
        User savedUser = userRepository.save(user);
        // Create wallet, send email, remove from pending
        // Return true
    }
}
```

#### `resendOtp()` Method
**Before:**
- Found user in database
- Generated new OTP
- Saved to database
- Sent email

**After:**
- First checks pending registrations
- If found: Generate new OTP, update pending registration, send email
- Falls back to registered users

```java
// Key flow:
if (pendingRegistrationService.hasPendingRegistration(email)) {
    String newOtp = generateOtp();
    pendingRegistrationService.resendOtpForPendingRegistration(email, newOtp);
    sendOtpEmail(email);
} else if (userRepository.findByEmail(email).isPresent()) {
    // ... existing logic
}
```

## Database Schema Changes
**No changes to User table schema are required.**

The following columns already exist and work as before:
- `email` - Now only populated after OTP verification
- `emailVerified` - Set to `true` only after OTP verification
- `otp` - Cleared after verification (remains `null` for verified users)
- `otpExpiry` - Cleared after verification (remains `null` for verified users)

## Flow Diagram

```
User Registration Flow:

1. User submits registration form
   ↓
2. AuthController: POST /register
   ↓
3. UserService.registerUser()
   ├─ Validate input
   ├─ Check email not in DB
   ├─ Check email not in pending registrations
   ├─ Generate OTP
   ├─ Create PendingRegistration
   ├─ Store in PendingRegistrationService (CACHE)
   ├─ Send OTP email
   └─ Return user object (NOT in DB)
   ↓
4. Redirect to /verify-otp?email=...
   ↓
5. User enters OTP
   ↓
6. AuthController: POST /verify-otp
   ↓
7. UserService.verifyOtp()
   ├─ Check PendingRegistrationService
   ├─ Verify OTP
   │
   ├─ If VALID:
   │  ├─ Create User from PendingRegistration
   │  ├─ Save to database ✓
   │  ├─ Create wallet
   │  ├─ Send welcome email
   │  ├─ Remove from pending registrations
   │  └─ Return true
   │
   └─ If INVALID:
      ├─ Return false
      └─ User stays in pending registrations (can retry)
   ↓
8a. If OTP valid: Redirect to dashboard / auto-login
8b. If OTP invalid: Show error, user can retry or resend OTP
   ↓
9. If OTP expires (10 minutes):
   ├─ Auto-cleanup removes pending registration
   └─ User can register again with same email


Background Task (Every 5 minutes):
├─ cleanupExpiredRegistrations()
├─ Remove all expired PendingRegistration entries
└─ Log number of removed entries
```

## Frontend Changes
**No changes required to HTML/JavaScript files** - the existing registration and OTP verification pages work as before.

The forms and flows remain the same:
1. Registration form → `/register`
2. OTP verification form → `/verify-otp`
3. Resend OTP → `/resend-otp`

## Testing Checklist

### Scenario 1: Successful Registration
- [ ] User registers with valid details
- [ ] OTP is sent to email
- [ ] User is in pending registrations (NOT in database yet)
- [ ] User enters correct OTP
- [ ] User is saved to database with `emailVerified = true`
- [ ] Wallet is created
- [ ] User can login

### Scenario 2: Failed OTP Verification
- [ ] User registers with valid details
- [ ] OTP is sent to email
- [ ] User enters wrong OTP
- [ ] User sees error message
- [ ] User remains in pending registrations
- [ ] User is NOT in database

### Scenario 3: Resend OTP for Pending Registration
- [ ] User registers and doesn't receive first OTP
- [ ] User clicks "Resend OTP"
- [ ] New OTP is sent (not saved to database)
- [ ] User can verify with new OTP
- [ ] User is saved to database

### Scenario 4: Duplicate Email (Different Users)
- [ ] User A registers with email@example.com
- [ ] User A doesn't verify OTP (stays pending)
- [ ] User B tries to register with same email
- [ ] System shows: "Registration already in progress for this email"
- [ ] User B must wait 10 minutes OR User A must verify

### Scenario 5: Expired OTP Cleanup
- [ ] User registers with email@example.com
- [ ] Wait 10+ minutes without verifying OTP
- [ ] Background cleanup removes pending registration
- [ ] User A can register again with same email (new attempt)

### Scenario 6: Backward Compatibility
- [ ] Existing admin users can still login
- [ ] Existing student users (already verified) can login
- [ ] OTP verification still works for existing users

## Key Benefits

1. **No Duplicate Email Errors**: Email only exists in database after verification
2. **Clean User Data**: Unverified registrations don't clutter the database
3. **Auto-Cleanup**: Expired pending registrations are automatically removed
4. **Backward Compatible**: Works with existing verified users
5. **Thread-Safe**: Uses `ConcurrentHashMap` for multi-threaded environment
6. **No Database Changes**: Uses existing User table schema

## Configuration

### OTP Validity Duration
Currently set to **10 minutes** in `registerUser()` method:
```java
LocalDateTime otpExpiry = LocalDateTime.now().plusMinutes(10);
```

Change the `10` to any desired minutes to adjust OTP validity.

### Cleanup Schedule
Currently runs every **5 minutes** in `PendingRegistrationService`:
```java
cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredRegistrations, 5, 5, TimeUnit.MINUTES);
```

Change the second and third `5` parameters to adjust cleanup frequency.

## Files Modified/Created

### Created:
- `src/main/java/com/pdfprinting/model/PendingRegistration.java` (NEW)
- `src/main/java/com/pdfprinting/service/PendingRegistrationService.java` (NEW)

### Modified:
- `src/main/java/com/pdfprinting/service/UserService.java`
  - Updated `registerUser()` method
  - Updated `verifyOtp()` method
  - Updated `resendOtp()` method
  - Added `createUserFromPendingRegistration()` private method
  - Added `@Autowired PendingRegistrationService`

### Not Modified (Backward Compatible):
- All controller files
- All HTML templates
- All other service files
- Database schema

## Troubleshooting

### Q: User sees "Registration already in progress for this email"
**A:** The user has a pending registration from within the last 10 minutes. They can:
1. Wait for OTP email and verify it
2. Click "Resend OTP" to get a new code
3. Wait 10+ minutes for auto-cleanup to remove the pending registration

### Q: User's data is not being saved even after OTP verification
**A:** Check the application logs for errors during OTP verification. Common reasons:
1. Database constraint violation (roll number, branch+division, etc.)
2. Wallet creation failed
3. Email service issue

### Q: "No user found with email" during OTP verification
**A:** Pending registration may have expired or been removed. User needs to register again.

## Future Enhancements

1. **Email Confirmation Link**: Add email verification link in addition to OTP
2. **SMS OTP**: Support SMS as alternative to email OTP
3. **OTP Attempts Limit**: Limit OTP verification attempts to prevent brute force
4. **Database Persistence**: Store pending registrations in a separate table instead of cache (for distributed systems)
5. **Analytics**: Track registration completion rates
