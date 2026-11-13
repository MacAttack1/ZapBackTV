This folder holds the Lumen TV signing key (lumen-tv.jks and keystore.properties).

- Every build uses it, so phone, PC and GitHub builds can all update each other.
- BACK IT UP. If you lose it, devices must uninstall once to switch to a new key.
- It is excluded from git on purpose; GitHub gets it from encrypted repository secrets.

See BUILDING.md for details.
