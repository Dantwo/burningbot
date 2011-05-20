(ns burningbot.db
  "database functions for burningbot"
  (:require [clojureql.core :as ql]
            [clojure.contrib.sql :as sql]
            [clj-time.coerce]
            [clj-time.core :as time]
            [burningbot.settings :as settings]))

;; utilities

(defn maybe-fn
  "wraps a function in a when test for its sole argument; if the argument is nil then
   the function is not called."
  [f & r]
  #(when (not (nil? %)) (apply f % r)))

(def maybe-from-date (maybe-fn clj-time.coerce/from-date))

(def maybe-to-date (maybe-fn clj-time.coerce/to-date))

(defn start-of-day
  [^org.joda.time.DateTime date]
  (time/date-time (time/year date) (time/month date) (time/day date) 0 0 0))

(defn end-of-day
  [^org.joda.time.DateTime date]
  (time/date-time (time/year date) (time/month date) (time/day date) 23 59 59 999))

;; database

(def db (settings/read-setting :database))


(defmacro Q
  "This macro elides the body of any query if db settings are not available."
  ([body] `(Q ~body ()))
  ([body default]
     `(if db
        (do ~body)
        ~default)))


;; tables
(def facts (ql/table db :facts))

(def logmarks (ql/table db :logmarks))

(def tags (ql/table db :tags))

(def tag-refs (ql/table db :tag_ref))

(def content-types {::facts "f"
                    ::logmarks "l"})


;; facts
(defn- fact-predicate
  "where clause to limit fact selection by channel and key"
  [channel key]
  (ql/where (and (= :channel channel)
                 (= :name key))))

(defn query-fact
  "returns all facts that match the channel and key"
  [channel key]
  (Q @(-> facts
          (ql/select (fact-predicate channel key))
          (ql/project [:value]))))

(defn insert-fact!
  "insert or update a facto for the channel and key"
  [channel key value creator]
  (Q (ql/update-in! facts
                    (fact-predicate channel key)
                    {:name key :channel channel :value value :creator creator})))

(defn delete-fact!
  "removes a fact from the database for the given channel and key"
  [channel key]
  (Q (ql/disj! facts (fact-predicate channel key))))

(defn query-fact-names
  "returns a seq of all the fact names for this channel"
  [channel]
  (Q @(-> facts
          (ql/select (ql/where (= :channel channel)))
          (ql/project [:name]))))


;; tags
(defn query-tagged
  "returns the ids of all the items for the content type that match the given tag"
  [tag content-type]
  (let [content-type (content-types content-type)]
    (-> tags
        (ql/select (ql/where (= :tag tag)))
        (ql/join tag-refs (ql/where (and (= :id :tag_id)
                                         (= :content_type content-type))))
        (ql/project [:tag_ref.content_id]))))

(defn query-facts-by-tag
  "returns all the facts for a given tag and channel"
  [channel tag]
  (Q (-> (query-tagged tag ::facts)
         (ql/join facts (ql/where (= :facts.id :content_id)))
         (ql/select (ql/where (= :channel channel)))
         (ql/project [:facts.name :facts.value :facts.creator :facts.channel]))))

;; logmarks

(defn insert-and-get-id!
  "Theoretically clojureql supports returning the last insert id as meta data on inserts etc
   but as that has not been working for me, this function will suffice."
  [db table-key record]
  (Q (sql/with-connection db
       (sql/transaction
        (sql/insert-records table-key record)
        (sql/with-query-results result ["SELECT Last_Insert_Id() as LastID;"]
          (-> result first :lastid))))))

(defn- get-tag-id
  [tag_name]
  (if-let [id (-> @(ql/select tags (ql/where (= :tag tag_name))) first :id)]
    id
    (insert-and-get-id! db :tags {:tag tag_name})))

(defn insert-logmark!
  "inserts a new log mark into the database and associates some tags with it."
  [{:keys [channel author start end tags]}]
  (Q (sql/with-connection db
       (sql/transaction
        (let [id (insert-and-get-id! db :logmarks
                                     {:channel channel
                                      :author  author
                                      :start   (maybe-to-date start)
                                      :end     (maybe-to-date end)})
              tag_ids (into {} (map (juxt identity get-tag-id) tags))
              tagref {:content_id id
                      :content_type (content-types ::logmarks)}]
          (ql/conj! tag-refs (map #(assoc tagref :tag_id (tag_ids %)) tags)))))))


(defn query-logmarks-for-day
  "Returns all the log marks for a particular channel and day. This is the inverse of insert-logmark!"
  [channel date]
  (Q (let [start-date (-> date start-of-day clj-time.coerce/to-date)
           end-date   (-> date end-of-day clj-time.coerce/to-date)
           records    @(-> logmarks
                           (ql/select (ql/where (and (or (and (>= :start start-date)
                                                              (<= :start end-date))
                                                         (and (>= :end start-date)
                                                              (<= :end end-date)))
                                                     (= :channel channel))))
                           (ql/join tag-refs (ql/where (and (= :content_id :logmarks.id)
                                                            (= :content_type (content-types ::logmarks)))))
                           (ql/join tags (ql/where (= :tag_ref.tag_id :tags.id)))
                           (ql/project [:id :channel :author :start :end :tags.tag]))
           marks      (group-by :id records)]
       (map (fn [[_ [{:keys [start end channel author channel] :as m} & _ :as marks]]]
              {:start   (maybe-from-date start)
               :end     (maybe-from-date end)
               :tags    (map :tag marks)
               :author  author
               :channel channel}) marks))))
