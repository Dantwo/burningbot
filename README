# burningbot

A bot for [#burningwheel](http://burningwheel.org/wiki/index.php?title=Burning_Wheel_IRC). This bot provides a number of simple functions from dice rolling to fact remembering and site scraping. 

`burningbot` is written in [clojure](http://clojure.org/) and uses [leiningen](http://github.com/technomancy/leiningen) for project and dependancy management.

For other irc bots written in clojure see [clojurebot](https://github.com/hiredman/clojurebot) and [sexpbot](https://github.com/cognitivedissonance/sexpbot).

## Usage

For in-channel usage, please see the [BW wiki](http://burningwheel.org/wiki/index.php?title=Burning_Bot). 

To get started running your own burningbot instance you need to:

   1. Clone this repository with git. 
   2. Install lein following the link above.
   3. Run `lein deps` in the root of this project to get the appropriate clojure version and  
      dependancies installed for this project.
   4. Create a 'logs' directory in the root of the project (or alternatively, place it somewhere else 
      and specify that location in your local settings file: see below.)
   5. create a file called `settings.clj-map` in the project root. 
   6. type `lein repl` to start an interactive shell
       1. type `(use :reload-all 'burningbot.core)` and press enter at the repl. this loads the core 
          namespace.
       2. type `(start-bot)` and press enter. This should cause the bot to attempt to connect.
       3. at any time you can enter `(use :reload-all 'burningbot.core)` to reload the code for the bot
          while running and it should immediately pick up any changes (there are a few exceptions to 
          this, in particular you may need to manually tell the bot to reload settings).
       
   
### Settings files

`burningbot` uses files containing Clojure map literals for its configuration. You can see the basic example in [`default-settings.clj-map`](resources/default-settings.clj-map). For local development you need to specify at least a name for your bot, password for ident, and starting-channels. It is good form not bring your development bot into #BurningWheel. eg:

    {:irclj {:password "your bots password here"
             :name     "your bots nick here"
             :username "your bots username here"}
     :starting-channels ["#burningbot"]
     :ignore-set #{"burningbot", "toastbot"}}

place this into `settings.clj-map` in the project root.

You can see more about settings in `src/burningbot/settings.clj`.

# Future

Possible future features:

 * More scraper rules
 * make the phrasebook not suck. It is currently one of the oldest pieces of code in the project. It 
   probably shouldnt look for a text file in resources either as this breaks if uberjared.
 * Dice
   * 'traitors' command: rerolls failed dice for persona
 * Logging. Basic logging with simple rotation exists. Additional features may be added:
   * Marking
      - some syntax to tell burningbot to record a mark with one or more tags. 
        potentially allows a mark to bracket a period of minutes.
      - all marks are tagged with the nick as well.
 * Join rooms
   *  Only joins a room when requested by an op of that room.
   *  Max limit to rooms
 
## License

Copyright (C) 2011 Andrew Brehaut 

Distributed under the [Eclipse Public License](epl-v10.html), the same as Clojure uses. 

