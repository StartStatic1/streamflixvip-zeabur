# Regras de ofuscação/minificação pro build de release.
# Vazio por ora — Retrofit/Moshi/Media3 já publicam suas próprias regras
# de consumer-proguard embutidas nas dependências, então na maioria dos
# casos não é preciso configurar nada manualmente aqui. Se algum crash de
# release aparecer relacionado a reflection (ex: Moshi não conseguindo
# desserializar um data class), é aqui que se adiciona a regra -keep
# específica.
