# GitHub Integration Setup Guide

This guide will help you set up GitHub integration for the PDF Printing System.

## Prerequisites

1. A GitHub account
2. A GitHub repository (public or private)
3. A GitHub Personal Access Token

## Step 1: Create a GitHub Repository

1. Go to [GitHub](https://github.com) and sign in
2. Click the "+" icon in the top right corner
3. Select "New repository"
4. Choose a repository name (e.g., `pdf-printing-storage`)
5. Set it as Private (recommended for file storage)
6. Click "Create repository"

## Step 2: Generate a Personal Access Token

1. Go to GitHub Settings → Developer settings → Personal access tokens → Tokens (classic)
2. Click "Generate new token (classic)"
3. Give it a descriptive name (e.g., "PDF Printing System")
4. Set expiration as needed
5. Select the following scopes:
   - `repo` (Full control of private repositories)
   - `public_repo` (Access public repositories) - if using public repo
6. Click "Generate token"
7. **Important**: Copy the token immediately (you won't see it again)

## Step 3: Configure Environment Variables

Set the following environment variables:

### For Development (application.properties or IDE):
\`\`\`properties
GITHUB_TOKEN=your_personal_access_token_here
GITHUB_REPOSITORY=your_username/your_repository_name
