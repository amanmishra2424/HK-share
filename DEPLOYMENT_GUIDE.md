# Quick Deployment Steps

## What Changed

**3 simple changes to fix OTP timeout issues:**

1. **SMTP Port 465** instead of 587 (more reliable in production)
2. **Async Email Sending** - OTP sent in background, registration completes instantly
3. **Better UI** - Loading states and cleaner OTP verification

## Files to Deploy

```
src/main/resources/application.properties      ← Updated (SMTP settings)
src/main/java/.../PdfPrintingApplication.java  ← Updated (@EnableAsync)
src/main/java/.../EmailService.java            ← Updated (@Async on sendOtpEmail)
src/main/resources/templates/auth/register.html        ← Updated (loading spinner)
src/main/resources/templates/auth/verify-otp.html      ← Updated (cleaner UI)
```

## Build Command

```bash
mvn clean package -DskipTests
```

## Deployment

1. Stop current application
2. Backup current .jar file
3. Copy new jar from `target/pdf-printing-app-0.0.1-SNAPSHOT.jar`
4. Start application

## Verification

1. Go to registration page
2. Fill form and click "Create Account"
3. Should see "Sending OTP..." message
4. Should redirect to OTP page within 2 seconds
5. Check email for OTP
6. Enter OTP on verification page
7. Should be logged in and redirected to dashboard

## Rollback (if needed)

If something goes wrong:
1. Stop application
2. Restore backup .jar
3. Restart application

---

## Expected Behavior After Fix

| Step | Before | After |
|------|--------|-------|
| Click "Create Account" | Form submitted, then hangs waiting for email | Form submitted, "Sending OTP..." shown, quick redirect |
| Register page waits | 30+ seconds | < 2 seconds |
| Email send | Blocks user | Happens in background |
| OTP verification | Old verify link UI | Clean 6-digit OTP entry |

---

## Support

If SMTP still times out:
- Check firewall allows port 465 outbound
- Verify email credentials in application.properties
- Check Gmail account has "Less secure app access" enabled
- Or use Gmail app password instead of account password
