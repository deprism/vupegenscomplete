VUPE LUCKPERMS SETUP

LuckPerms is the authority for donor and staff groups in Vupe MAX.

After VupeCore, LuckPerms, Vault, PlaceholderAPI and the economy provider are loaded:

  /vupe luckperms bootstrap

VupeCore reads:
  plugins/VupeCore/ranks.yml
  plugins/VupeCore/staff.yml

and creates/configures:
Donor: echo, cipher, phantom, titan, vupeplus
Staff: helper, moderator, srmod, admin, sradmin, manager, developer, owner, builder, media

Then verify:
  /lp listgroups
  /lp user YOURNAME parent add owner
  /lp user YOURNAME info

Do NOT also maintain an independent Vupe permission database. VupeCore stores
gameplay rank identity/bonuses, while LuckPerms is the permission/prefix authority.

TAB reads %luckperms-prefix%.
