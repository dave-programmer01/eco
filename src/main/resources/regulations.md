# Starter Compliance Corpus (ECA)
# Each rule is a self-contained chunk. Format: [ID] SOURCE — TITLE :: rule text.
# Small on purpose: enough distinct rules to prove retrieval returns the RELEVANT one.

[GDPR-5] GDPR Art. 5 — Data minimisation :: Personal data must be adequate, relevant, and limited to what is necessary for the purposes for which it is processed. Collecting or retaining data beyond that purpose is non-compliant.

[GDPR-17] GDPR Art. 17 — Right to erasure :: The data subject has the right to obtain erasure of their personal data without undue delay where the data is no longer necessary, consent is withdrawn, or the data was unlawfully processed. Contracts that deny or indefinitely delay deletion of personal data conflict with this right.

[GDPR-28] GDPR Art. 28 — Processor obligations :: A data processor must act only on documented instructions from the controller, ensure confidentiality, and not engage another processor without prior authorisation. Vendor agreements lacking a data processing agreement (DPA) are a compliance gap.

[GDPR-44] GDPR Art. 44 — Cross-border transfer :: Transfer of personal data to a third country is only lawful with adequate safeguards (adequacy decision, standard contractual clauses, or binding corporate rules). Clauses permitting unrestricted international data sharing are high risk.

[HIPAA-514] HIPAA 45 CFR 164.514 — De-identification / minimum necessary :: Protected health information (PHI) must be limited to the minimum necessary to accomplish the intended purpose. Broad disclosure of PHI without de-identification or a minimum-necessary limitation is non-compliant.

[HIPAA-308] HIPAA 45 CFR 164.308 — Business associate safeguards :: A covered entity may disclose PHI to a business associate only with a written business associate agreement (BAA) requiring appropriate safeguards. Sharing PHI with a vendor absent a BAA is a violation.

[SOX-302] SOX Section 302 — Disclosure controls :: Principal officers must certify the accuracy of financial reports and the effectiveness of internal disclosure controls. Agreements that obscure liabilities or move obligations off the books undermine this certification.

[LIAB-UNCAP] General contract risk — Uncapped liability :: A clause exposing a party to unlimited or uncapped liability for any and all damages, without a limitation-of-liability cap, is a high financial-exposure risk and should be flagged for negotiation.

[INDEM-BROAD] General contract risk — Broad indemnification :: An indemnification clause requiring one party to indemnify the other against all claims, including those arising from the indemnified party's own negligence, is an unusually broad risk allocation and should be reviewed.

[TERM-AUTO] General contract risk — Auto-renewal / evergreen :: An auto-renewal clause that renews the term indefinitely unless cancelled within a narrow window, with no cap on price increases, creates lock-in risk and unbudgeted cost exposure.
