# Composants tiers

## Police Nunito (`app/src/main/res/font/nunito_semibold_italic.ttf`)

Nunito, © The Nunito Project Authors (https://github.com/googlefonts/nunito),
distribuée sous SIL Open Font License 1.1 (https://openfontlicense.org).
Utilisée pour la traduction des paroles.

## Empreinte audio Shazam (`app/src/main/java/com/karaokedrive/recognition/shazam/`)

Portage en Kotlin de l'algorithme de calcul d'empreinte de **shazamio**
(https://github.com/dotX12/ShazamIO), distribué sous licence MIT, reproduite ci-dessous.

L'algorithme d'origine provient de **SongRec** (https://github.com/marin-m/SongRec),
distribué sous licence GPL-3.0. ⚠️ Avant toute diffusion de l'application, vérifier la
compatibilité de licence (le portage suit le comportement de SongRec sur l'étalement temporel).

L'API de reconnaissance de Shazam utilisée est **non officielle** : usage personnel uniquement.

---

MIT License

Copyright (c) 2021 dotX12

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## Paroles mot par mot : Kugou (`app/src/main/java/com/karaokedrive/lyrics/KugouClient.kt`, `KrcConverter.kt`)

Service de paroles de **Kugou** (API **non officielle**, usage personnel uniquement), interrogé
en complément de LRCLIB pour obtenir le timing de chaque mot. Le format KRC (chiffrement XOR
+ zlib, balises `<décalage,durée,0>`) est documenté notamment par **Lyricify-Lyrics-Helper**
(https://github.com/WXRIW/Lyricify-Lyrics-Helper, licence Apache-2.0) ; aucun code n'en est repris.
