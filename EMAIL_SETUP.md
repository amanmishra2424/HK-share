# Email System Setup Guide (Brevo)

This guide explains how to configure transactional email for the PDF Printing System using Brevo (formerly Sendinblue). The instructions cover both local development and production deployments on Render.

---

## 1. Prerequisites

- A Brevo account with **Transactional Email** enabled
- At least one **verified sender email address** in Brevo (Settings → Senders & IP → Senders)
- An SMTP key (Settings → SMTP & API → Generate SMTP key)

---

## 2. Required Environment Variables

| Key | Description | Suggested Value |
| --- | --- | --- |
| `EMAIL_ENABLED` | Turns email features on/off | `true` |
| `BREVO_SMTP_HOST` | Brevo SMTP host | `smtp-relay.brevo.com` |
| `BREVO_SMTP_PORT` | SMTP port (TLS) | `587` |
| `BREVO_SMTP_USERNAME` | SMTP login | The Brevo SMTP login shown beside your API key |
| `BREVO_SMTP_PASSWORD` | SMTP password | The SMTP key generated in Brevo |
| `APP_MAIL_FROM_ADDRESS` | From address shown to users | A verified sender email (e.g. `no-reply@yourdomain.com`) |
| `APP_MAIL_FROM_NAME` | Display name in inbox | e.g. `Print For You` |

> 🔐 **Security Tip**: Never commit secrets to Git. Use environment variables locally via `.env` (ignored by Git) and managed secrets in Render.

---

## 3. Local Development Setup

1. Create a `.env` file in the project root (listed in `.gitignore`).
2. Populate it with the variables above. Example:

```properties
EMAIL_ENABLED=true
BREVO_SMTP_HOST=smtp-relay.brevo.com
BREVO_SMTP_PORT=587
BREVO_SMTP_USERNAME=your_smtp_login
BREVO_SMTP_PASSWORD=your_smtp_key
APP_MAIL_FROM_ADDRESS=your-verified-sender@domain.com
APP_MAIL_FROM_NAME=Print For You
```

3. Start the application. The `EmailConfig` runner will automatically send a self-test email on startup to confirm the configuration.

---

## 4. Render Production Setup

1. Open your Render dashboard → **Services** → select the web service.
2. Navigate to **Environment** → **Environment Variables**.
3. Add the variables listed in Section 2.
4. Redeploy the service to apply the changes.
5. Check Render logs for `Testing email configuration...` to confirm the self-test succeeded.

> ✅ Render already allows outbound SMTP on port 587, so no additional firewall changes are needed.

---

## 5. Troubleshooting Checklist

- **Self-test fails on startup**
	- Confirm the SMTP key is active (Regenerate in Brevo if unsure).
	- Make sure the sender email is verified and matches `APP_MAIL_FROM_ADDRESS`.
	- Ensure TLS is enabled; the app already sets StartTLS and TLSv1.2.
- **Emails not delivered**
	- Check Brevo dashboard → Transactional → Logs for the event status.
	- Verify the recipient address is not blocked or bounced.
- **Still seeing Gmail references in logs?**
	- Restart the application after updating environment variables.

---

## 6. Verifying End-to-End

1. Trigger an OTP email by starting the registration flow.
2. Trigger a password reset email from the login page.
3. Check Brevo transactional logs to confirm sends.
4. Confirm all emails arrive with the correct from name and reply address.

If all steps pass, the email system is ready for production traffic on Brevo.

