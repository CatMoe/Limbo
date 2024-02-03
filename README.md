# Limbo

A separate virtual server bootstrap (Powered by Blessing). Support enabling multi listeners, custom MiniMessage motd, and more.

## Feature

- Fully supported 1.7.x-1.20.4
 - Fully customizable MOTD (brand, player list, online, max-online, etc.)
 - Multi-listener support. (You do not need to enable another Limbo instance.)
 - Can set the header and footer in the player list (Tab).
 - Can send title, subtitle, chat messages, action bar when joining.
 - Hex color support. (MiniMessage, required 1.16+ client)
 - Each listener has its own config. and support reload. Listeners can be added or removed at any time.

## ⚠️ Bot Flood / Attack Warning!

The Limbo does not have the ability to deal with any attacks.  
If a large number of bots flood into Limbo at the same time or request the status of Limbo. 
You may be forced to disconnect in an instant because Limbo is taking up all the upstream bandwidth.  
There's nothing we can do about it. If you happen to be in this situation. 
Please deploy a reverse proxy and install an antibot plugin for yourself. Or use something like TCPShield.

## TODO

 - [x] Support info forwarding from BungeeCord/BungeeGuard
 - [ ] Support Modern (Velocity) info forwarding.
 - [ ] Multiple Motd can be configured in a single listener. And use one at random when requested.
 - [ ] Throttler or something like that to alleviate spam.
