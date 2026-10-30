# SPIKE-2 — Payment Provider, Escrow Hold and Creator Payout Research

**Project:** Flavors  
**Task:** SPIKE-2 — Payment Provider Research  
**Decision:** CONDITIONAL GO — MontyPay

## 1. Objective

Investigate payment providers capable of supporting USD card payments, payment holding, refunds, and creator payouts in Lebanon within two business days. The research supports the payment workflows defined in UC-F7.1, UC-F7.2, and UC-F8.1.

## 2. Providers Evaluated

Two payment providers were evaluated: MontyPay and Whish Money.

### 2.1 MontyPay

MontyPay provides documented APIs for online card payments, two-stage payment processing, capture, and refunds.

Its two-stage payment processing mechanism (DMS) allows a payment to be authorized before the funds are captured. After authorization, the payment enters a pending state. A subsequent capture request settles the transaction.

MontyPay also provides a sandbox environment for testing successful payments, declined payments, authorization and capture, and refunds without transferring real funds.

However, DMS authorization is a temporary hold and does not establish long-term escrow functionality. The availability of automated payouts to Lebanese creators within two business days has not been confirmed.

### 2.2 Whish Money

Whish Money provides business services including online checkout, payment collection, and payroll solutions.

Its checkout services allow businesses to receive online payments, while its payroll solutions support transfers to employee accounts.

These services make Whish a potential alternative for local creator payouts. However, the available public information does not confirm an automated API for paying independent creators, escrow functionality, merchant refund APIs, or a guaranteed payout period of two business days.

## 3. Provider Comparison

| Requirement | MontyPay | Whish Money |
|---|---|---|
| Online payments | Documented | Supported through Checkout Services |
| USD card payment eligibility | Requires merchant confirmation | Requires merchant confirmation |
| Payment authorization and capture | Documented through DMS | Not confirmed |
| Long-term escrow holding | Not confirmed | Not confirmed |
| Refund functionality | Documented | API not confirmed |
| Local payouts | Not confirmed | Business payroll service advertised |
| Automated creator payout API | Not confirmed | Not confirmed |
| Payout within two business days | Not confirmed | Not confirmed |
| Sandbox environment | Documented | Public access not verified |

## 4. Proposed Payment Workflow

The proposed Flavors payment workflow consists of the following stages:

1. A restaurant initiates a USD payment for an influencer campaign.
2. The payment is processed through the selected payment provider.
3. Flavors records the payment and its current status in its internal database.
4. Following confirmation of the required payment state, the campaign amount is recorded as held and unavailable for creator payout.
5. Once the campaign is approved, the creator's earnings are calculated after deducting the Flavors platform fee.
6. The creator payout is initiated through an available payment or transfer service.
7. If a refund is approved, the appropriate refund operation is initiated through the payment provider.

A ledger-based hold is proposed for tracking collected funds awaiting release. However, the actual custody and control of collected funds must be permitted by the payment provider and applicable requirements. An internal database status alone does not constitute financial escrow.

## 5. Sandbox and Proof-of-Concept Feasibility

MontyPay provides sandbox testing capabilities and documents the following operations:

- Successful card payment.
- Two-stage payment authorization.
- Payment capture using `/payment/capture`.
- Refund of a settled transaction using `/payment/refund`.

The sandbox provides test card scenarios, including a successful transaction using card number `4111 1111 1111 1111` with expiry `01/38`.

For DMS testing, an authorized transaction initially returns `order_status=pending`. A successful capture subsequently returns `order_status=settled`.

However, executing these operations requires merchant-specific credentials, including a sandbox URL, Merchant Test Key, password, and a merchant configuration supporting DMS.

These credentials are obtained through the merchant administration system or account manager and are not publicly available in the documentation.

**Proof-of-concept status:** Not executed. The documented sandbox workflow was reviewed, but no authenticated payment, capture, or refund requests were performed.

A local mock payment workflow may be used to demonstrate the proposed Flavors payment states if accepted as a substitute for provider sandbox testing. Such a demonstration would not verify MontyPay's live API integration.

## 6. Fallback Solution

If automatic creator payouts are unavailable, Flavors may use a manual payout process.

Under this approach:

1. The restaurant payment is collected through the approved payment provider.
2. The payment is tracked in the Flavors database as awaiting campaign approval.
3. Once the campaign is approved, the creator's earnings are calculated.
4. An authorized Flavors administrator initiates the payout manually through an available local transfer service, potentially Whish.
5. The payout reference, amount, and completion status are recorded in the system.

The feasibility of meeting the two-business-day payout requirement through this process remains unverified. Any arrangement involving the collection or holding of funds on behalf of creators requires appropriate provider authorization and compliance with applicable requirements.

## 7. Limitations

- MontyPay sandbox credentials are not publicly provided.
- No authenticated sandbox transactions have been executed.
- Long-term escrow holding has not been confirmed for either provider.
- Automated payouts to Lebanese creators remain unverified.
- Neither provider has been confirmed to guarantee payouts within two business days.
- Whish's payroll services do not establish API support for independent creator payouts.
- Merchant onboarding and provider approval may be required before implementation.
- Actual payment fees and settlement arrangements have not been established.

## 8. Conclusion

**Decision: CONDITIONAL GO — MontyPay**

MontyPay is the preferred candidate for Flavors' initial payment integration because its documentation provides the clearest support for card payment processing, authorization and capture, refunds, and sandbox testing.

Whish Money is retained as a potential fallback for manual creator payouts in Lebanon.

This decision is conditional because long-term fund holding, payout eligibility, payout timing, and provider sandbox integration have not been fully verified.

The research supports selecting a provisional payment architecture, but it does not establish that all payment requirements can currently be met.

## 9. References

1. [MontyPay — Testing and Sandbox](https://docs.montypay.com/docs/guides/get_started/testing_and_sandbox/)
2. [MontyPay — Authentication and Credentials](https://docs.montypay.com/docs/guides/get_started/authentication_and_credentials/)
3. [MontyPay — API Documentation](https://docs.montypay.com/)
4. [Whish Money — Corporate Solutions](https://www.whish.money/corporate-solutions)
5. [Whish Money — Official Website](https://www.whish.money/)
