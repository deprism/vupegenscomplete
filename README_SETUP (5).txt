VUPE 2.1 VAULT SETUP

Install Vault as the compatibility API.

Do NOT install a separate economy provider.

VupeCore registers its native Vupe Money economy with Vault during startup.

Verify:
  /vupe integrations
  /vupe doctor
  /bal
  /eco give YOURNAME 1000

If Vault is missing, Vupe Money still functions internally, but plugins that
only know the Vault API cannot access it.
