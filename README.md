# Projet de Construction de Make GNU Distribué

## Description du Projet

Ce projet consiste à développer une version distribuée de **Make** en utilisant **Java** et **Apache Spark**.  
Il vise à exploiter une architecture **Master-Worker** afin de paralléliser l'exécution des tâches décrites dans un **Makefile**.

### Objectifs
- Modéliser un Make distribué en respectant la **localité des fichiers**.
- Gérer l'exécution des tâches avec **RDDs Spark**.
- Assurer une **tolérance aux pannes** et un déploiement automatique.
- Proposer une version avec et sans **NFS** pour comparaison.

---

## Fonctionnalités Clés

### Architecture Master-Worker
- **Version avec NFS** :  
   - Construction d’un graphe de dépendances via l'algorithme de Kahn.  
   - Exécution parallèle des tâches par niveau (RDD Spark).  
- **Version sans NFS** :  
   - Calcul complet des dépendances (directes et indirectes).  
   - Utilisation de serveurs de fichiers pour le transfert.  
   - Création de répertoires temporaires pour chaque cœur.

---

## Performances et Tests Réalisés

### Modèle Théorique et Expérimental
Les performances ont été évaluées sur les clusters de Grid5000 selon trois tests :  
1. **Test 7** : Exécution réelle sur un Makefile de 3 niveaux avec calculs.  
2. **Test 8** : Analyse du coût ajouté par le **driver Spark**.  
3. **Test 9** : Comparaison entre le modèle théorique et les performances réelles.

Les résultats montrent une bonne **scalabilité horizontale** tout en identifiant des limites liées à Spark, telles que :
- L’exécution par niveaux imposée par RDDs.
- Des problèmes de mémoire sur des Makefiles complexes, surtout dans la version sans NFS.

---

## Execution et tests sur le make
Vous trouverez plus de détails dans le fichier CahierDeLaboratoire.pdf, et sur les slides de la Présentation.