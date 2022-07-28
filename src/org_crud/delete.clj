(ns org-crud.delete
  (:require
   [org-crud.update :as up]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Delete items
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn delete-from-file!
  "Deletes the item passed, if a match is found in the path"
  [path item]
  (up/update! path item :delete-item))

(defn delete-item!
  "Deletes the item passed, if a match is found in the path"
  [item]
  (let [org-path (:org/source-path item)]
    (if org-path
      (delete-from-file! org-path item)
      (do
        (println item)
        (println "Item delete attempted for bad org-path" org-path)))))
