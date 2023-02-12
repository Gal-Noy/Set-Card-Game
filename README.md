# Set Card Game

A digital version of the classic Set card game, where players race to find as many sets as possible before the deck runs out.

## Getting Started

- Make sure Java is installed (1.8 or later).
- Clone or download the repository to your local machine.
- Edit the configuration file as you wish - you should edit the numbers of human/computer players, set their names and most important, the duration of each round:
  - by setting TurnTimeoutSeconds > 0 (60 by default), the game will start in regular countdown timer mode.
  - by setting TurnTimeoutSeconds = 0, the game will start in a "Free play" mode, withot timer.
  - by setting TurnTimeoutSeconds < 0, the game will start in "Elapsed" mode, where the timer resets after a legal set is collected.
 ### Commands
- To compile: mvn clean compile test
- To run: java -cp target/classes bguspl.set.Main

## Keyboard & Interface
![image](https://user-images.githubusercontent.com/109943831/218310054-1a63cc6f-a86d-478e-be11-0a45419e7c8c.png)
![image](https://user-images.githubusercontent.com/109943831/218310096-55f31b0c-98f0-4e32-b991-725de975c1db.png)


## Gameplay

The goal of the game is to find as many sets as possible before the deck runs out. A set is defined as three cards where each feature is either all the same or all different for all three cards.
- Color
- Shape
- Number
- Fill

![image](https://user-images.githubusercontent.com/109943831/218310141-3e4f902f-acb1-4453-9b8a-fa2275e2c849.png)
