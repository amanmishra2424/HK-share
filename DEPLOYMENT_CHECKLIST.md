# Quick Deployment Checklist

## Pre-Deployment

### Code Changes ✅
- [x] OTP verification flow implemented
- [x] Academic year made required
- [x] PendingRegistrationService created
- [x] UserService updated
- [x] AuthController updated
- [x] All models updated
- [x] Project compiles successfully (mvn clean compile)

### Configuration
- [ ] Email service configured in application.properties
- [ ] Database connections verified
- [ ] JWT secret configured
- [ ] Admin email and password set in properties

### Database
- [ ] academic_year column exists in users table
- [ ] academic_year column exists in pdf_uploads table
- [ ] Both columns are NOT NULL
- [ ] Existing null values updated (if any)

## Deployment

### Build
```bash
mvn clean package
# Should create target/pdf-printing-app-0.0.1-SNAPSHOT.jar
```

### Start Application
```bash
java -jar target/pdf-printing-app-0.0.1-SNAPSHOT.jar
# Or use: mvn spring-boot:run
```

### Verify Services
- [ ] Application starts without errors
- [ ] Admin user created (check logs)
- [ ] Email service initialized
- [ ] Database connection established

## Testing

### Registration Flow
1. [ ] Navigate to http://localhost:8080/register
2. [ ] Form displays with Academic Year dropdown
3. [ ] Fill form with all required fields
4. [ ] Submit registration
5. [ ] Redirected to /verify-otp page
6. [ ] Check email for OTP
7. [ ] Enter OTP and verify
8. [ ] Auto-redirected to dashboard
9. [ ] Check database - user has academicYear

### PDF Upload
1. [ ] Login as registered user
2. [ ] Go to upload page
3. [ ] Select and upload PDF
4. [ ] Verify upload success (no null error)
5. [ ] Check admin dashboard organization by year

### Error Cases
1. [ ] Try registering with existing email - see "already registered" error
2. [ ] Enter wrong OTP - see "invalid OTP" error
3. [ ] Wait 10+ min without OTP entry - see "expired" error
4. [ ] Try registering without academic year - form validation blocks it

## Post-Deployment

### Monitoring
```bash
# Check logs for errors
tail -f application.log | grep ERROR
tail -f application.log | grep DEBUG

# Monitor pending registrations in cache
# (Check PendingRegistrationService.getAllPendingRegistrations() if exposed)
```

### Admin Tasks
- [ ] Verify users can upload PDFs
- [ ] Check admin dashboard shows PDFs organized by year/branch/batch
- [ ] Test batch processing and merging by year
- [ ] Monitor database for any null academicYear values

## Rollback Plan

If issues occur:
```bash
# Revert to previous version
git revert <commit-hash>

# Or restore from backup
./restore-from-backup.sh
```

## Success Indicators

✅ Users can register and verify email  
✅ No duplicate email errors on abandoned registrations  
✅ All PDF uploads have academicYear (no null errors)  
✅ Admin dashboard shows hierarchical organization  
✅ PDFs can be merged by year/branch/batch  
✅ No errors in application logs  

## Documentation

- [x] OTP_VERIFICATION_FLOW_FIX.md - Detailed OTP implementation
- [x] OTP_QUICK_REFERENCE.md - Quick reference guide
- [x] ACADEMIC_YEAR_FIX.md - Academic year requirement details
- [x] COMPLETE_IMPLEMENTATION_GUIDE.md - Full technical guide
- [x] This checklist

## Support

For issues:
1. Check application logs for error messages
2. Use `/debug-user?email=test@example.com` endpoint
3. Verify email configuration in application.properties
4. Check database schema matches models
5. Verify all environment variables set

---

**Status**: Ready for Deployment ✅
**Date**: October 18, 2025
