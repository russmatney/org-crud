(ns org-crud.core
  (:require
   [organum.core :as org]
   [clojure.walk :as walk]
   [clojure.string :as string]
   [babashka.fs :as fs]
   [org-crud.node :as node]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parse helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn parse-org-file
  [path]
  (try
    (-> path fs/absolutize str org/parse-file)
    (catch Exception ex
      (println "org-crud.core/parse-org-file exception" path)
      (println ex)
      nil)))

(comment
  (parse-org-file "repos.org"))

(defn parse-org-lines
  "Very close to the internal org/parse-file function,
  except that it expects a seq of text lines.

  Used to get basic text into the item's 'body' structure
  for commit messages and other non-org sources.
  "
  [lines]
  (when (seq lines)
    (reduce #'org/handle-line [(#'org/root)] lines)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parsing flattened items
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn parsed->flattened-items
  "Only parses :type :section (skipping :root).

  Produces flattened items, rather than nested.
  This means deeper org nodes will not be contained within parents.
  "
  [source-file parsed]
  (when (and parsed source-file)
    (reduce
      (fn [items next]
        (conj items (merge
                      ;; {:org-section next}
                      (node/->item next source-file))))
      []
      parsed)))

(defn path->flattened-items
  "Returns a flattened list of org items in the passed file.

  Produces flattened items, rather than nested.
  This means deeper org nodes will not be contained within parents.
  See `path->nested-items`."
  [p]
  (->> p
       parse-org-file
       (parsed->flattened-items p)
       (remove nil?)
       (remove (comp nil? :org/name))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parsing nested items
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
                                                 (let [item (-> top-of-stack
                                                                (assoc :org/parent-name
                                                                       (->> ctx-stack
                                                                            (map :org/name)
                                                                            (string/join " > "))))]
                                                   (-> parent (update :org/items conj item)))))))
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

    (walk/postwalk
      (fn [node]
        (if (and (map? node)
                 (seq (:org/items node)))
          (-> node
              (update :org/items (fn [items]
                                   (->> items
                                        ;; TODO get this order right on the first pass?
                                        reverse

                                        ;; attach relative indexes
                                        (map-indexed vector)
                                        (map (fn [[i item]]
                                               (-> item
                                                   (assoc :org/relative-index i))))))))
          node)))))

(comment
  (->
    (flattened->nested
      [{:org/level :level/root :org/name "root"}
       {:org/level 1 :org/name "b"}
       {:org/level 1 :org/name "a"}
       {:org/level 2 :org/name "c"}
       {:org/level 3 :org/name "d"}
       {:org/level 4 :org/name "e"}
       {:org/level 2 :org/name "f"}
       {:org/level 1 :org/name "g"}])
    println)

  (walk/postwalk (fn [x]
                   (if (and (map? x)
                            (seq (x :items)))
                     (update x :items reverse)
                     x))
                 [{:level 1 :name "a"}
                  {:level 1 :name "b" :items
                   [{:level 2 :name "f"}
                    {:level 2 :name "c"
                     :items [{:level 3 :name "d"
                              :items [{:level 4 :name "e"}]}]}]}
                  {:level 1 :name "g"}]))

(defn parsed->nested-items
  "Parses level-1 items with sub-sections as children"
  [source-file parsed]
  (when (and source-file parsed)
    (->> parsed
         ;; (filter #(= :section (:type %)))
         (map (fn [raw] (node/->item raw source-file)))
         flattened->nested)))

(defn path->nested-item
  "Produces a single item, the :root of the org file.
  Level-1 sections can be found as :items, all children are nested as
  further lists of :items all the way down.

  If you want the org file as a flattened list, see `path->flattened-items`.
  "
  [p]
  (some->> p
           parse-org-file
           (parsed->nested-items p)
           (remove nil?)
           first))

(defn dir->nested-items
  "For now, :recursive? goes one layer down.

  TODO impl with loop to recur properly."
  ([dir] (dir->nested-items {} dir))
  ([opts dir]
   (->> dir
        fs/list-dir
        (map (fn [f]
               (if (and (:recursive? opts)
                        (not (string/includes? f ".git"))
                        (fs/directory? f))
                 (fs/list-dir f)
                 [f])))
        (apply concat)
        (filter #(contains? #{".org"} (fs/extension %)))
        (map path->nested-item)
        (remove nil?))))

(comment
  (-> (dir->nested-items "/home/russ/Dropbox/notes")
      count)
  (-> (dir->nested-items {:recursive? true} "/home/russ/Dropbox/notes")
      count)

  (-> (dir->nested-items "/home/russ/Dropbox/notes")
      first))
