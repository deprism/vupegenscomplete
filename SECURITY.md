# Vupe 2.1 security and transaction safety

## Secrets

Never commit:

```text
Discord bot tokens
Tebex secrets
API keys
database passwords
webhook secrets
auth tokens
```

Rotate any real credential that existed in a historical server archive.

## Money

VupeCore owns the Money ledger.

Vault is only the API bridge.

Do not install a second Vault economy provider.

## Auction escrow

The native AH removes the full current bid when a bidder becomes highest.

If there was a previous highest bidder, their previous bid is refunded before
the new bidder is stored.

The persisted auction record therefore represents the currently held escrow.

When an auction settles:

```text
winner -> persisted claim item
seller -> winning Money
```

No-bid items move to the seller's persisted expired-item queue.

## Auction claims

A queued item is removed only after the complete stack fits and is delivered.

## Crates

A selected crate reward is persisted before the animation completes.

It is removed from the pending queue only after successful reward delivery.

A broken configured reward removes the pending marker and refunds one crate
key, preventing both loss and repeated login refunds.

## Shop

Generic material selling avoids Vupe PDC-tagged custom items.

Money amounts reject non-finite/invalid transaction values.

## Paid store

Use:

```text
/vupegrant
```

from console/Tebex.

Do not let a storefront directly modify JSON or LuckPerms storage.

## Staff

LuckPerms remains the staff permission authority.

Keep `vupe.admin` and fulfillment permissions restricted.
