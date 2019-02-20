# Coinz-I.L.P.2018
This is my GitHub repository for the Coinz project of the Informatics Large Practical course at the University of Edinburgh

# Introduction
This is a location-based game, where the player tries to collect as many coins of different currencies as possible each day and converts
them in GOLD. 

# Aims
The player with the most GOLD in the Bank is ranked first. 

# Gameplay Rules
Only the first 25 collected coins are cashed in to the player's Bank
account. Any additional collected coins are stored as "Spare Change" and can be sent to another player using the player's email address, 
or tossed to waste.
If a player chooses to toss any "Spare Change", a 75% fine is applied to any Spare Change coins received from other players.
The map is updated with new coins and new Currency Rates for each different Currency every day at midnight. Moreover, the Bank balances of
GOLD for each player are updated every day on midnight, after automatically converting the player's coins to GOLD with using that day's 
Currency Rates.

# Coin Fever Mode
Each player can also play exactly one session of "Coin Fever Mode" per day, where he/she runs against a 3-minute clock to collect 10 or more coins.
On success, the collected coins will be converted to GOLD. On failure, the player wins nothing from that mode.

# Under the Hood
The back-end database service of the app is Google's Cloud Firestore. The maps of coins and currency rates are parsed from JSON Files
stored on the Informatics School Server, created by our lecturer and course organiser. 
