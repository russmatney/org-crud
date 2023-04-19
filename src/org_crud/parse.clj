(ns org-crud.parse
  (:require
   [clojure.walk :as walk]
   [clojure.string :as string]
   [clojure.java.io :as io]
   [babashka.fs :as fs]

   #_[organum.core :as organum.core]
   [org-crud.node :as node]))

(defn classify-line
  "Classify a line for dispatch to handle-line multimethod."
  [ln]
  (let [headline-re       #"^(\*+)\s*(.*)$"
        pdrawer-re        #"^\s*:(PROPERTIES|END):"
        pdrawer           (fn [x] (second (re-matches pdrawer-re x)))
        pdrawer-item-re   #"^\s*:([0-9A-Za-z_\-]+):\s*(.*)$"
        block-re          #"^\s*#\+(BEGIN|END|begin|end)_(\w*)\s*([0-9A-Za-z_\-]*)?.*"
        block             (fn [x] (rest (re-matches block-re x)))
        def-list-re       #"^\s*(-|\+|\s+[*])\s*(.*?)::.*"
        ordered-list-re   #"^\s*\d+(\.|\))\s+.*"
        unordered-list-re #"^\s*(-|\+|\s+[*])\s+.*"
        metadata-re       #"^\s*(CLOCK|DEADLINE|START|CLOSED|SCHEDULED):.*"
        table-sep-re      #"^\s*\|[-\|\+]*\s*$"
        table-row-re      #"^\\s*\\|.*"
        inline-example-re #"^\s*:\s.*"
        horiz-re          #"^\s*-{5,}\s*$"]
    (cond
      (re-matches headline-re ln)             :headline
      (string/blank? ln)                      :blank
      (re-matches def-list-re ln)             :definition-list
      (re-matches ordered-list-re ln)         :ordered-list
      (re-matches unordered-list-re ln)       :unordered-list
      (= (pdrawer ln) "PROPERTIES")           :property-drawer-begin-block
      (= (pdrawer ln) "END")                  :property-drawer-end-block
      (re-matches pdrawer-item-re ln)         :property-drawer-item
      (re-matches metadata-re ln)             :metadata
      (#{"BEGIN" "begin"} (first (block ln))) :begin-block
      (#{"END" "end"} (first (block ln)))     :end-block
      (= (second (block ln)) "COMMENT")       :comment
      (= (first ln) \#)                       #_ (not (= (second ln) \+)) :comment
      (re-matches table-sep-re ln)            :table-separator
      (re-matches table-row-re ln)            :table-row
      (re-matches inline-example-re ln)       :inline-example
      (re-matches horiz-re ln)                :horizontal-rule
      :else                                   :paragraph)))

(defn strip-tags
  "Return the line with tags stripped out and list of tags"
  [ln]
  (if-let [[_ text tags] (re-matches #"(.*?)\s*(:[\w:]*:)\s*$" ln)]
    [text (remove string/blank? (string/split tags #":"))]
    [ln nil]))

(defn strip-keyword
  "Return the line with keyword stripped out and list of keywords"
  [ln]
  (let [keywords-re #"()?"
        words       (string/split ln #"\s+")]
    (if (re-matches keywords-re (words 0))
      [(string/triml (string/replace-first ln (words 0) "")) (words 0)]
      [ln nil])))

;; node constructors

(defn node [type]
  {:type type :content []})

(defn line [type text]
  {:line-type type :text text})

;; State helpers

(defn subsume
  "Updates the current node (header, block, drawer) to contain the specified
   item."
  [state item]
  (let [top (last state)
        new (update-in top [:content] conj item)]
    (conj (pop state) new)))

(defn subsume-top
  "Closes off the top node by subsuming it into its parent's content"
  [state]
  (let [top   (last state)
        state (pop state)]
    (subsume state top)))

(defn create-new-section [ln]
  (when-let [[_ prefix text] (re-matches  #"^(\*+)\s*(.*?)$" ln)]
    (let [[text tags] (strip-tags text)
          [text kw]   (strip-keyword text)]
      (merge (node :section)
             {:level (count prefix) :name text :tags tags :kw kw}))))

(defn parse-block [ln]
  (let [block-re             #"^\s*#\+(BEGIN|END|begin|end)_(\w*)\s*([0-9A-Za-z_\-]*)?"
        [_ _ type qualifier] (re-matches block-re ln)]
    ;; TODO get other key values on blocks?
    (merge (node :block)
           {:block-type type :qualifier qualifier})))

;; handle line

(defmulti handle-line
  "Parse line and return updated state."
  (fn [_state ln] (classify-line ln)))

(defmethod handle-line :headline [state ln]
  (conj state (create-new-section ln)))

(defmethod handle-line :begin-block [state ln]
  (conj state (parse-block ln)))

(defmethod handle-line :end-block [state _ln]
  (subsume-top state))

(defmethod handle-line :property-drawer-begin-block [state _ln]
  (conj state (node :drawer)))

(defmethod handle-line :property-drawer-end-block [state _ln]
  (subsume-top state))

(defmethod handle-line :default [state ln]
  (subsume state (line (classify-line ln) ln)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 'nesting' an ordered, flattened list of parsed org nodes
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn add-node-to-parent
  "Returns `parent` with `node` attached.

  Updates `node` with some `parent` and `parent-stack` specific details."
  [node parent parent-stack]
  (let [item (-> node
                 ;; set any parent ids
                 ;; TODO probably stick with a stack here
                 ;; so consumers can get a nearest-parent or furthest-parent
                 (assoc :org/parent-ids (->> parent-stack (map :org/id) (remove nil?) (into #{})))
                 ;; set a nested parent name
                 (assoc :org/parent-name (->> parent-stack
                                              (map (fn [parent]
                                                     (or (:org/name-string parent)
                                                         (:org/source-file parent))))
                                              (remove nil?)
                                              (string/join " > ")))
                 (assoc :org/parent-names (->> parent-stack
                                               (map (fn [parent]
                                                      (or (:org/name-string parent)
                                                          (:org/source-file parent))))
                                               (remove nil?)
                                               (into []))))]
    (-> parent (update :org/items conj item))))

(defn flattened->nested
  "Returns top-level items with sub-items as children"
  [xs]
  (->>
    (loop [remaining  xs
           items      []
           ctx-stack  []
           last-level 0]
      (let [{:org/keys [level] :as current-node} (some-> remaining first)
            level                                (if (#{:level/root} level) 0 level)
            level-diff                           (when current-node (- level last-level))]
        (if (and current-node (> level-diff 0))
          ;; we moved in -> at least 1 level, push current node and recur with new level
          (recur (rest remaining) items (conj ctx-stack current-node) level)

          ;; reduce over the level-diff or ctx-stack
          (let [{:keys [ctx-stack items]}
                (reduce
                  (fn [{:keys [ctx-stack items]} _]
                    (let [top-of-stack (peek ctx-stack)
                          ;; remove top from stack
                          ctx-stack    (when top-of-stack (pop ctx-stack))

                          ;; add top-of-stack to parent items
                          parent    (when (seq ctx-stack)
                                      (some-> ctx-stack peek
                                              ((fn [parent]
                                                 ;; update the item with the parent/stack context
                                                 (add-node-to-parent top-of-stack parent ctx-stack)))))
                          ;; replace parent in stack with updated parent
                          ctx-stack (when parent
                                      (-> ctx-stack pop (conj parent)))

                          ;; update items
                          items (cond
                                  ;; if we have a parent, don't touch items
                                  ;; (we already added top-o-s to the parent items)
                                  parent items

                                  ;; otherwise, add top-of-stack to items (presumably top-level)
                                  top-of-stack (conj items top-of-stack)

                                  :else items)]
                      {:ctx-stack ctx-stack
                       :items     items}))
                  {:ctx-stack ctx-stack
                   :items     items}
                  (if current-node
                    ;; pop as many times as diff in level
                    (range level-diff 1)
                    ;; pop as many times as diff in level
                    ctx-stack))]
            (if current-node
              ;; recur with updated
              (recur (rest remaining) items (conj ctx-stack current-node) level)
              ;; if no current-node (no `remaining`), return items
              items)))))

    ;; walk the created items to include some final node updates
    (walk/postwalk
      (fn [node]
        (if (not (and (map? node) (seq (:org/items node))))
          node
          (-> node
              (update :org/items
                      (fn [items]
                        (->> items
                             ;; TODO get this order right on the first pass?
                             reverse

                             ;; attach relative indexes
                             (map-indexed vector)
                             (map (fn [[i item]]
                                    (-> item (assoc :org/relative-index i)))))))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parse
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn parse-lines
  "Parses a seq of strings into a single org-crud node.
  Returns a single root node.

  Expects to be handed lines in order from a single file,
  but maybe be useful as a parser for org content in other situations.
  "
  ([lines] (parse-lines lines nil))
  ([lines path]
   (when (seq lines)
     (some->> lines
              #_(reduce #'organum.core/handle-line [(#'organum.core/root)])
              (reduce handle-line [(node :root)])
              ;; TODO bake node/->item logic into the handle-line helpers
              (map #(node/->item % path))
              flattened->nested
              (remove nil?)
              first))))

(defn parse-file
  [path]
  (try
    (with-open [rdr (io/reader (-> path fs/expand-home str))]
      (parse-lines (line-seq rdr) (fs/expand-home path)))
    (catch Exception ex
      (println "org-crud.parse/parse-file exception" path)
      (println ex)
      nil)))

(comment
  (parse-file (str (fs/home) "/todo/readme.org"))
  (parse-file "~/todo/readme.org")
  (parse-file (str (fs/home) "/todo/daily/2022-09-26.org"))
  (parse-file (str (fs/home) "/todo/daily/2022-09-27.org"))
  (parse-file (str (fs/home) "/todo/journal.org")))
