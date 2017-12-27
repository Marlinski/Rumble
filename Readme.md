#Rumble
A decentralized and delay-tolerant twitter-like mobile application. 

[<img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="60">](https://f-droid.org/app/org.disrupted.rumble)
<a href="https://play.google.com/store/apps/details?id=org.disrupted.rumble"><img src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png" height="60"></a>

## Abstract

Rumble enables the spread of messages in an epidemic fashion using an automatically formed and opportunistic local ad-hoc network. Every message sent or received with Rumble is stored on the local database and pushed to every other device it meets. By doing so, messages naturally propagate throughout the network using social links as the underlying infrastructure. Because it doesn't rely on any fixed infrastructure like the Internet, it is naturally resistant against censorship. 

Because a large number of messages may exist, Rumble prioritizes messages based on various parameters such as hashtag subscriptions, replication density, date of creation, etc.

By default, everyone belongs to the "rumble.public" group which is open but Rumble also enables the possibility to create private communities encrypted with AES-128 (CBC/PKCS5). A message that belongs to a private community only propagates through members of that community, and a new member can only join a community after being invited or "vouched" by another member. 

Rumble also supports a real-time "chat" to exchange messages with the current neighborhood only. In this mode, messages are not forwarded any further. 


## Technical Details

Whenever Rumble is running, it automatically discovers its neighborhood (Bluetooth and Wi-Fi) and forms local area networks. Each time a new communication channel is created between two devices, they exchange their "preferences" and start exchanging messages with each other. 

## License

[GNU General Public License version 3](https://github.com/Marlinski/Rumble/blob/master/LICENSE.txt)
