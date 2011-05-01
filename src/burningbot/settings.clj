(ns burningbot.settings
  "This settings module provides an api to load settings. These settings are a
   clojure map created by loading and merging maps in order."
  
  (:require [clojure.java.io :as io]
            [clj-time.core :as time])
  (:use [clojure.contrib.map-utils :only [deep-merge-with]])
  (:import [java.net URL]
           [java.io File PushbackReader]))

(def ^{:dynamic true} *settings-sources*
  [(io/resource "default-settings.clj-map")
   "./settings.clj-map"])

(defonce settings (atom nil))

(defn- load-map
  "reads a map from some source"
  [item]
  (let [m (cond (or (string? item)
                    (instance? URL item)
                    (instance? File item)) (try
                                             (with-open [r (-> item io/reader PushbackReader.)]
                                               (read r))
                                             (catch Exception _ nil))
                    (map? item) item
                    (ifn? item) (item))]
    (when (map? m) m)))

(defn load-settings!
  "reads in the contents of *settings-sources* seq into the settings atom.

   * a String or URL is treated as a filename and is openned and read.
   * a File is read.
   * an IPersistentMap is treated as a literal.
   * an IFn is called and the result must be a map.
   * anything else is ignored

   Keys in items found later in the seq override those that occur earlier.

   if provided no arguments it uses *settings-sources* otherwise
   if an argument is provided that is used instead of *settings-sources*."
  ([]
     (load-settings! *settings-sources*))
  ([settings-sources]
     (reset! settings
             {:last-updated (time/now)
              :settings    (apply deep-merge-with (fn [a b] b) {}
                                  (for [item settings-sources
                                        :let [m (load-map item)]
                                        :when m] m))})))

(defn- settings-map
  []
  (or @settings (load-settings!)))

(defn read-setting
  "returns a setting for a given key"
  ([key] (read-setting key nil))
  ([key default]
     (or ((if (keyword? key)
            get
            get-in) (:settings (settings-map)) key)
         default)))

(defn last-updated
  "returns the time the settings were last updated"
  []
  (:last-updated (settings-map) :last-updated))
