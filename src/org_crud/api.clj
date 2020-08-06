(ns org-crud.api
  (:require
   [org-crud.create :as create]
   [org-crud.update :as update]
   [org-crud.core :as core]
   [org-crud.delete :as delete]
   [org-crud.refile :as refile]))


(def add-item! create/add-item!)
(def delete-item! delete/delete-item!)
(def refile! refile/refile!)
(def update! update/update!)

(def path->nested-item core/path->nested-item)
(def dir->nested-items core/dir->nested-items)
(def path->flattened-items core/path->flattened-items)
