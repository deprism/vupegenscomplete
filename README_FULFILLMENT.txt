VUPE TEBEX / WEB STORE FULFILLMENT

Tebex is optional but recommended for real-money store fulfillment.

Vupe's store GUI is a catalog/preview. Actual payments should occur on your
secure web store. Configure package commands in Tebex to call Vupe's audited
fulfillment API.

Examples:
  vupegrant {username} rank:echo
  vupegrant {username} rank:cipher
  vupegrant {username} rank:phantom
  vupegrant {username} rank:titan
  vupegrant {username} rank:vupeplus

  vupegrant {username} crystals:75000
  vupegrant {username} bundle:starter_store
  vupegrant {username} bundle:grinder_store
  vupegrant {username} bundle:overdrive_store
  vupegrant {username} bundle:founder

  vupegrant {username} autosellchest:3
  vupegrant {username} crate:vupe:5
  vupegrant {username} sellmulti:0.10

Use the actual Tebex username placeholder shown by your Tebex control panel.
VupeCore's command accepts an offline player name for account-data grants.
Physical-item grants require the player to be online unless your Tebex package
uses a Vupe bundle containing a later/manual grant workflow.

Change plugins/VupeCore/store.yml:
  store.url: https://YOUR-STORE-DOMAIN
