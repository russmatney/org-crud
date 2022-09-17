(ns user
  (:require
   [org-crud.update :as upd]
   [org-crud.core :as org]
   [wing.repl :as w.repl]
   #_[clomacs :as clomacs]
   [clojure.string :as string]
   [babashka.fs :as fs]
   [babashka.process :as process]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Ensuring uuids on all nodes

(defn ensure-id [item]
  (let [id
        (or (:id item)
            (-> item :props :id)
            (str (java.util.UUID/randomUUID)))]
    (-> item
        (assoc :id id)
        (assoc-in [:props :id] id))))

(comment
  (def --roam-dir "/home/russ/Dropbox/notes")

  (org/dir->nested-items --roam-dir)
  (upd/update-dir-with-fn! {:dir        --roam-dir
                            :recursive? true} ensure-id)
  )


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; renaming org-roam files to move timestamps into property drawers

(defn org->starts-with-datetime? [org]
  ((comp (fn [fname]
           (or
             (string/starts-with? fname "2020")
             (string/starts-with? fname "2021")))
         fs/file-name :org/source-file)
   org))

(defn fname->data [s]
  (let [groups
        (->>
          (re-seq #"(\d\d\d\d)(\d\d)(\d\d)(\d\d)(\d\d)(\d\d)-([\w_.]+)" s)
          first
          (drop 1))
        new-fname                     (last groups)
        [year month day hour min sec] (->> groups (take 6) (into []))]
    {:new-filename new-fname
     :created-at   (str year month day ":" hour min sec)}))

(comment
  (def --dir "/home/russ/Dropbox/todo/garden")

  (def --ex-str "20201228124352-readme_idioms.org")

  (fname->data --ex-str)

  (->>
    (org/dir->nested-items --dir)
    (filter org->starts-with-datetime?)
    ;; (map (comp fs/file-name :org/source-file))
    (map (comp :org/source-file))
    ;; (take 3)
    count
    )

  (->>
    (org/dir->nested-items --dir)
    (filter org->starts-with-datetime?)
    ;; (take 2)
    (map (fn [org]
           (let [
                 {:keys [new-filename created-at]} ((comp fname->data fs/file-name :org/source-file) org)
                 file-dir                          (-> org :org/source-file (#(string/replace % (fs/file-name %) "")))
                 new-filepath                      (str file-dir new-filename)]
             ;; (println "new-filename" new-filename)
             ;; (println "filedir" file-dir)
             ;; (println "new-filepath" new-filepath)

             ;; update props
             (upd/update! (:org/source-file org) org {:org.prop/created-at created-at})

             ;; move file
             (->
               ^{:out :string
                 :dir file-dir}
               (process/$ mv ~(-> org :org/source-file) ~new-filepath)
               process/check
               :out)))))
  )


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; misc

(comment
  (w.repl/sync-libs!)


  (defn missing-id [item]
    (and (not (:id item))
         (not (-> item :props :id))))

  (upd/update-dir-with-fn!
    {:dir --roam-dir}
    (fn [item]
      (when (missing-id item)
        (println "missing id!" (:source-file item))
        (let [id (str (java.util.UUID/randomUUID))]
          {:id id :props {:id id}}))))



  ;; (clomacs/clomacs-defn
  ;;   emacs-version
  ;;   emacs-version)

  ;; (println (emacs-version))

  ;; (clomacs/clomacs-defn
  ;;   clmx-current-org-item
  ;;   clmx-current-org-item)

  ;; (println (clmx-current-org-item))

  )
