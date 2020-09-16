(ns org-crud.headline
  (:require
   [org-crud.util :as util]
   [clojure.string :as string]
   [org-crud.fs :as fs]))


(def ^:dynamic *multi-prop-keys* #{})
(def ^:dynamic *prop-parser*
  "Contains some types with known parses.
  For now supports a few string->dates parses." {})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; headline helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ->body-as-strings
  "Parses content :table-row :line-types into a list of strs"
  [{:keys [content]}]
  (->> content
       (filter #(= (:line-type %) :table-row))
       (map :text)))

(defn ->body-string [raw]
  (->> (->body-as-strings raw)
       (string/join "\n")))

(defn ->body
  "Produces a somewhat structured body,
  with support for source blocks.

  Filters drawers.
  "
  [{:keys [content]}]
  (->> content
       (remove #(= :drawer (:type %)))
       (flatten)))

(defn ->metadata
  [{:keys [content]}]
  (->> content
       (filter (comp #(= % :metadata) :line-type))
       (map :text)))

(defn ->drawer
  [{:keys [content]}]
  (->> content
       (filter (comp #(= % :drawer) :type))
       first
       :content
       (map :text)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; property drawers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ->prop-key [text]
  (let [[key _val] (string/split text #" " 2)]
    (when key
      (-> key
          (string/replace ":" "")
          (string/replace "_" "-")
          (string/replace "+" "")
          (string/replace "#" "")
          string/lower-case
          keyword))))

(defn ->prop-value [text]
  (when-let [key (->prop-key text)]
    (let [[_k val] (string/split text #" " 2)]
      (when val
        (let [val (string/trim val)]
          (if-let [parser (*prop-parser* key)] (parser val) val))))))

(defn ->properties [x]
  (let [prop-lines
        (cond
          (= :section (:type x))
          (->drawer x)

          (= :root (:type x))
          ;; TODO stop after first non-comment?
          (->> x
               :content
               (filter #(= (:line-type %) :comment))
               (map :text)))]
    (if (seq prop-lines)
      (->> prop-lines
           (group-by ->prop-key)
           (map (fn [[k vals]]
                  (let [vals (map ->prop-value vals)
                        vals (if (contains? *multi-prop-keys* k)
                               ;; sorting just for testing convenience
                               (sort vals)
                               (first vals))]
                    [k vals])))
           (into {}))
      {})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; headline parsers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ->level [{:keys [level]}]
  (or level :level/root))

(comment
  (keyword (str "level/" 1))
  (keyword (str "level/" :root))
  )

(defn ->id [hl]
  (-> hl ->properties :id))

(defn ->name [{:keys [name type]}]
  (cond
    (and name (= type :section))
    (->> name
         (re-find
           #"\*?\*?\*?\*? ?(?:TODO|DONE|CANCELLED)? ?(.*)")
         second
         ((fn [s] (string/replace s #"\[[ X-]\] " ""))))))

(comment
  (->name
    {:type :section
     :name
     "[[file:20200627150518-spaced_repetition_in_decision_making.org][Spaced-repetition]] for accumulating a design or an approach"})
  (->name {:name "* CANCELLED Reduce baggage" :type :section})
  (->name {:name "* TODO Reduce baggage" :type :section})
  (->name {:name "* [X] Reduce baggage" :type :section})
  (->name {:name "* Reduce baggage"})
  (let [raw
        {:type    :root,
         :content [{:line-type :comment, :text "#+TITLE: finally, a wiki!" }
                   {:line-type :blank, :text "" }
                   {:line-type :table-row, :text
                    "We finally arrived at [file:path.org][name-for-path]"}
                   {:line-type :blank, :text ""}
                   {:line-type :table-row, :text "It's a wiki"}]}]
    (->name raw)))

(defn ->raw-headline [{:keys [name level]}]
  (when level
    (str (apply str (repeat level "*")) " " name)))

(defn ->tags [{:keys [type content tags]}]
  (cond
    (= :root type)
    (let [text
          (some->>
            content
            (filter (fn [c]
                      (and (= (:line-type c) :comment)
                           (string/includes?
                             (-> c :text string/lower-case)
                             "#+roam_tags"))))
            first
            :text)]
      (when text
        (some->
          (->> text
               string/lower-case
               (re-find #".*roam_tags: (.*)"))
          second
          (string/split #" ")
          set)))

    :else (-> tags (set))))

(defn ->todo-status
  [{:keys [name]}]
  (when name
    (cond
      (re-seq #"(\[-\])" name)
      :status/in-progress

      (re-seq #"(\[ \]|TODO)" name)
      :status/not-started

      (re-seq #"(\[X\]|DONE)" name)
      :status/done

      (re-seq #"CANCELLED" name)
      :status/cancelled

      :else nil)))

(comment
  (->todo-status
    {:name "[X] parse/pull TODOs from repo files"}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; url parsing
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ->urls
  ;; parses an org header for urls from the name and body
  [x]
  (let [strs (conj (->body-as-strings x) (->name x))]
    (->> strs
         (map util/->url)
         (remove nil?))))

(comment
  (util/->url "read http://github.com"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; word count
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn str->count [str]
  (when str
    (some->
      (string/split str #" ")
      count))
  )

(defn ->word-count [item raw]
  (+ (or (some-> item :org/name str->count) 0)
     (or (some->> raw
                  ->body-as-strings
                  (map str->count)
                  (reduce + 0)) 0)))

(comment
  (->word-count nil nil)
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; item - a general headline
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ->item [raw source-file]
  (-> (cond
        (= :section (:type raw))
        {:org/level       (->level raw)
         :org/source-file (-> source-file fs/absolute str)
         :org/id          (->id raw)
         :org/name        (->name raw)
         :org/headline    (->raw-headline raw)
         :org/tags        (->tags raw)
         :org/body        (->body raw)
         :org/body-string (->body-string raw)
         :org/status      (->todo-status raw)
         :props           (->properties raw)}

        (= :root (:type raw))
        (let [props (->properties raw)]
          {:org/level       (->level raw)
           :org/source-file (-> source-file fs/absolute str)
           :org/name        (:title props)
           :org/tags        (->tags raw)
           :org/body        (->body raw)
           :props           props
           :org/id          (:id props)}))
      ((fn [item]
         (when item
           (-> item
               (assoc :org/word-count (->word-count item raw))
               (assoc :org/urls (-> raw ->urls set))))))))
