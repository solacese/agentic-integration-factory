# Security Policy

## Supported Versions

We release security patches for the following versions:

| Version | Supported          |
| ------- | ------------------ |
| main    | :white_check_mark: |
| < 0.1.0 | :x:                |

## Reporting a Vulnerability

We take security vulnerabilities seriously. If you discover a security issue, please report it responsibly.

### How to Report

**Please do NOT open a public GitHub issue for security vulnerabilities.**

Instead, report security issues privately through one of these channels:

1. **GitHub Security Advisories** (preferred):
   - Go to the [Security tab](https://github.com/solacese/agentic-integration-factory/security/advisories)
   - Click "Report a vulnerability"
   - Provide detailed information about the vulnerability

2. **Email**:
   - Send details to: security@solace.com
   - Include "Security: Agentic Integration Factory" in the subject line

### What to Include

When reporting a vulnerability, please include:

- Description of the vulnerability
- Steps to reproduce the issue
- Potential impact and severity
- Any suggested fixes or mitigations
- Your contact information for follow-up

### Response Timeline

- **Initial Response**: Within 2 business days
- **Assessment**: Within 5 business days
- **Fix Timeline**: Depends on severity
  - **Critical**: Within 7 days
  - **High**: Within 14 days
  - **Medium**: Within 30 days
  - **Low**: Next scheduled release

### Disclosure Policy

- We will acknowledge your report within 2 business days
- We will provide regular updates on our progress
- Once a fix is ready, we will coordinate disclosure timing with you
- We will credit you in the security advisory (unless you prefer to remain anonymous)

## Security Best Practices

When using this project:

1. **Never commit sensitive credentials** to version control
2. **Use environment variables** for API keys, tokens, and passwords
3. **Rotate credentials regularly**, especially for production deployments
4. **Enable authentication** on all publicly exposed services
5. **Keep dependencies updated** by regularly running `make install`
6. **Review generated code** before deploying to production
7. **Use HTTPS/TLS** for all network communications
8. **Implement rate limiting** on public API endpoints

## Scope

The following are considered in scope for security reports:

- Authentication and authorization bypasses
- SQL injection, XSS, or other injection attacks
- Remote code execution vulnerabilities
- Sensitive data exposure
- Denial of service vulnerabilities
- Insecure dependencies with known CVEs

The following are typically out of scope:

- Social engineering attacks
- Physical security issues
- Vulnerabilities in third-party services
- Issues requiring physical access to servers
- Theoretical vulnerabilities without proof of concept

## Known Security Considerations

- **Demo Mode**: The demo admin password feature (`DEMO_ADMIN_PASSWORD`) is intended for development only
- **Generated Code**: Always review generated micro-integrations before production use
- **Cloud Credentials**: EC2, Kubernetes, and Event Portal credentials must be securely managed
- **Database**: PostgreSQL should be properly secured with strong passwords and network restrictions

## Security Updates

Security patches will be released as needed and announced through:

- GitHub Security Advisories
- Release notes
- Project README

Subscribe to repository notifications to stay informed about security updates.

## Questions?

For general security questions that don't involve reporting a vulnerability, you can:

- Open a public GitHub discussion
- Contact the maintainers through standard channels

Thank you for helping keep the Agentic Integration Factory secure!