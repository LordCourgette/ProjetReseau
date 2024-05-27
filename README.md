# PROJET Réseau

## Présentation
Ce rapport présente le projet de "Distributed Mining" réalisé dans le cadre du cours
de Réseaux de notre première année de Master MIAGE. Le projet vise à
implémenter un système permettant de déléguer une tâche de recherche de hash à
plusieurs workers et de consolider les résultats. Ce document détaille l'architecture,
les choix techniques, les difficultés rencontrées, les solutions trouvées, et la
répartition du travail au sein de l'équipe.
3

## Installation
### Prérequis:
- Avoir Java 11 ou supérieur installé sur la machine.

### Etape 1:
Clone le projet dans le répertoire de votre choix.
### Etape 2:
Se rendre dans ./ProjetReseau/reseau
### Etape 3:
Compiler le projet 
```bash
javac -d build/classes $(find src -name "*.java")
```
### Etape 4: 
Lancer le serveur
```bash
java -cp build/classes Server
```
### Etape 5:
Instancier les clients
```bash
java -cp build/classes Client
```
Dans autant de terminaux que vous souhaitez avoir de client.
### Etape 6:
Lancer une résolution
Dans le terminal d'execution du serveur, entrer la commande :
```
START n (numéro de la difficulté que vous souhaitez résoudre)
```
## Groupe
Agathe JINER, 
Sofiane MEBCHOUR, 
Fabrice ARNOUT
