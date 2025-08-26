# Email System Setup Guide

This guide will help you configure the email system for the PDF Printing System.

## Prerequisites

1. An email account (Gmail recommended)
2. SMTP server access
3. App-specific password (for Gmail)

## Step 1: Email Account Setup

### For Gmail:
1. Enable 2-Factor Authentication on your Google account
2. Go to Google Account Settings → Security → App passwords
3. Generate an app password for "Mail"
4. Use this app password (not your regular password)

### For Other Email Providers:
- Outlook/Hotmail: Use outlook.live.com SMTP settings
- Yahoo: Use smtp.mail.yahoo.com with app password
- Custom SMTP: Get settings from your email provider

## Step 2: Configure Environment Variables

Set the following environment variables:

### For Development:
\`\`\`properties
EMAIL_USERNAME=your-email@gmail.com
EMAIL_PASSWORD=your-app-password-here
