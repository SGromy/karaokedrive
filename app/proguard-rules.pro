# Règles R8 de la version optimisée (release).
# Les bibliothèques utilisées (OkHttp, coroutines, Car App, ML Kit, Compose) fournissent
# déjà leurs propres règles ; ce fichier ne contient que ce qui est propre à l'app.

# Numéros de ligne conservés dans les plantages, noms de fichiers masqués
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ML Kit instancie ses registrars par réflexion (constructeur sans argument). Depuis AGP 9,
# R8 (mode strict) ne garde plus ce constructeur implicitement : sans cette règle, la
# détection de langue plante au démarrage de la version optimisée.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
