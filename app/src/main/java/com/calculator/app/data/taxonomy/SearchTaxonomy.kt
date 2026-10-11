package com.calculator.app.data.taxonomy

/** En underkategori med de söktermer som används mot sajterna. */
data class Subcategory(
    val id: String,
    val label: String,
    val terms: List<String>
) {
    val primaryTerm: String get() = terms.firstOrNull().orEmpty()
}

/** En kategori med ett antal underkategorier. */
data class Category(
    val id: String,
    val label: String,
    val emoji: String,
    val description: String,
    val subcategories: List<Subcategory>
)

/**
 * Kategoriträdet som driver kategoriväljaren i sökningen. Etiketterna är svenska,
 * söktermerna engelska eftersom det är de taggar sajterna indexerar.
 *
 * Inga termer i trädet får matcha [com.calculator.app.ai.AgeSafety] – det verifieras i enhetstesterna.
 */
object SearchTaxonomy {

    val categories: List<Category> = listOf(
        Category(
            id = "appearance",
            label = "Utseende",
            emoji = "💇",
            description = "Hår, stil och detaljer",
            subcategories = listOf(
                Subcategory("blonde", "Blond", listOf("blonde")),
                Subcategory("brunette", "Brunett", listOf("brunette")),
                Subcategory("redhead", "Rödhårig", listOf("redhead")),
                Subcategory("black_hair", "Svarthårig", listOf("black hair")),
                Subcategory("long_hair", "Långt hår", listOf("long hair")),
                Subcategory("short_hair", "Kort hår", listOf("short hair")),
                Subcategory("freckles", "Fräknar", listOf("freckles")),
                Subcategory("ink", "Tatueringar & piercing", listOf("tattoo", "piercing")),
                Subcategory("natural_look", "Naturlig look", listOf("natural", "no makeup look")),
                Subcategory("glamour", "Glamour & smink", listOf("glamour", "makeup"))
            )
        ),
        Category(
            id = "body",
            label = "Kropp & figur",
            emoji = "💃",
            description = "Kroppsform och stil",
            subcategories = listOf(
                Subcategory("curvy", "Kurvig", listOf("curvy")),
                Subcategory("slim", "Slimmad", listOf("slim")),
                Subcategory("athletic", "Atletisk", listOf("athletic")),
                Subcategory("petite", "Petite", listOf("petite")),
                Subcategory("bbw", "Yppig (BBW)", listOf("bbw")),
                Subcategory("tall", "Lång", listOf("tall")),
                Subcategory("busty", "Stor byst", listOf("busty", "big tits")),
                Subcategory("fit_body", "Tränad kropp", listOf("fit body", "fitness")),
                Subcategory("long_legs", "Långa ben", listOf("long legs"))
            )
        ),
        Category(
            id = "ethnicity",
            label = "Etnicitet & stil",
            emoji = "🌍",
            description = "Ursprung och utseendestil",
            subcategories = listOf(
                Subcategory("asian", "Asiatisk", listOf("asian")),
                Subcategory("latina", "Latina", listOf("latina")),
                Subcategory("ebony", "Ebony", listOf("ebony")),
                Subcategory("indian", "Indisk", listOf("indian")),
                Subcategory("arab", "Arabisk", listOf("arab")),
                Subcategory("european", "Europeisk", listOf("european")),
                Subcategory("scandinavian", "Skandinavisk", listOf("scandinavian", "nordic"))
            )
        ),
        Category(
            id = "company",
            label = "Sällskap & antal",
            emoji = "👥",
            description = "Hur många och vilka som medverkar",
            subcategories = listOf(
                Subcategory("solo", "Solo", listOf("solo")),
                Subcategory("couple", "Par", listOf("couple")),
                Subcategory("threesome", "Trekant", listOf("threesome")),
                Subcategory("group", "Grupp", listOf("group sex")),
                Subcategory("orgy", "Orgie", listOf("orgy")),
                Subcategory("lesbian", "Lesbisk", listOf("lesbian")),
                Subcategory("gay", "Gay", listOf("gay")),
                Subcategory("bisexual", "Bisexuell", listOf("bisexual")),
                Subcategory("swinger", "Swinger & klubb", listOf("swinger", "club sex")),
                Subcategory("milf_mature", "Mogen (MILF)", listOf("milf", "mature"))
            )
        ),
        Category(
            id = "activity",
            label = "Aktivitet",
            emoji = "🔥",
            description = "Vad som händer",
            subcategories = listOf(
                Subcategory("hardcore", "Klassisk hardcore", listOf("hardcore")),
                Subcategory("oral", "Oralt", listOf("oral")),
                Subcategory("anal", "Analt", listOf("anal")),
                Subcategory("doggystyle", "Doggy", listOf("doggystyle")),
                Subcategory("missionary", "Missionären", listOf("missionary")),
                Subcategory("cowgirl", "Ridande", listOf("cowgirl")),
                Subcategory("massage", "Massage", listOf("massage")),
                Subcategory("squirt", "Squirt", listOf("squirt")),
                Subcategory("deepthroat", "Deep throat", listOf("deepthroat", "deep throat")),
                Subcategory("handjob", "Handjob", listOf("handjob"))
            )
        ),
        Category(
            id = "location",
            label = "Plats",
            emoji = "🏠",
            description = "Var det utspelar sig",
            subcategories = listOf(
                Subcategory("bedroom", "Sovrum", listOf("bedroom")),
                Subcategory("kitchen", "Kök", listOf("kitchen")),
                Subcategory("bathroom", "Badrum", listOf("bathroom")),
                Subcategory("shower_sauna", "Dusch & bastu", listOf("shower sauna")),
                Subcategory("outdoor", "Utomhus", listOf("outdoor")),
                Subcategory("car", "Bil", listOf("car")),
                Subcategory("office", "Kontor", listOf("office")),
                Subcategory("pool_beach", "Pool & strand", listOf("pool beach")),
                Subcategory("gym", "Gym", listOf("gym")),
                Subcategory("hotel", "Hotell", listOf("hotel"))
            )
        ),
        Category(
            id = "mood",
            label = "Stämning",
            emoji = "💫",
            description = "Tempot och känslan",
            subcategories = listOf(
                Subcategory("romantic", "Romantisk", listOf("romantic")),
                Subcategory("sensual", "Sensuell", listOf("sensual")),
                Subcategory("passionate", "Passionerad", listOf("passionate")),
                Subcategory("playful", "Lekfull", listOf("playful")),
                Subcategory("dominant", "Dominant", listOf("dominant")),
                Subcategory("submissive", "Underdånig", listOf("submissive")),
                Subcategory("slow_gentle", "Mjuk & långsam", listOf("slow gentle")),
                Subcategory("intense", "Intensiv", listOf("intense")),
                Subcategory("rough", "Rough", listOf("rough"))
            )
        ),
        Category(
            id = "roleplay",
            label = "Rollspel & kostym",
            emoji = "🎭",
            description = "Kläder och scenarier",
            subcategories = listOf(
                Subcategory("nurse", "Sjuksköterska", listOf("nurse")),
                Subcategory("police", "Polis", listOf("police")),
                Subcategory("boss_secretary", "Chef & assistent", listOf("boss secretary")),
                Subcategory("maid", "Hemhjälp", listOf("maid")),
                Subcategory("housewife", "Hemmafru", listOf("housewife")),
                Subcategory("bride", "Brud", listOf("bride")),
                Subcategory("cosplay", "Cosplay", listOf("cosplay")),
                Subcategory("latex_leather", "Latex & läder", listOf("latex leather")),
                Subcategory("lingerie", "Underkläder", listOf("lingerie")),
                Subcategory("stockings", "Strumpbyxor", listOf("stockings")),
                Subcategory("activewear", "Träningskläder", listOf("yoga pants", "workout"))
            )
        ),
        Category(
            id = "camera",
            label = "Kamera & vinkel",
            emoji = "🎥",
            description = "Hur det är filmat",
            subcategories = listOf(
                Subcategory("pov", "POV", listOf("pov")),
                Subcategory("closeup", "Närbild", listOf("close up")),
                Subcategory("facecam", "Ansiktskamera", listOf("face cam")),
                Subcategory("amateur_cam", "Amatörkamera", listOf("amateur camera")),
                Subcategory("handheld", "Handhållen", listOf("handheld")),
                Subcategory("mirror", "Spegel", listOf("mirror")),
                Subcategory("studio", "Professionell studio", listOf("studio")),
                Subcategory("casting", "Casting", listOf("casting")),
                Subcategory("backstage", "Behind the scenes", listOf("behind the scenes"))
            )
        ),
        Category(
            id = "production",
            label = "Kvalitet & produktion",
            emoji = "🎬",
            description = "Bildkvalitet och produktionstyp",
            subcategories = listOf(
                Subcategory("hd", "HD", listOf("hd")),
                Subcategory("uhd", "4K / UHD", listOf("4k")),
                Subcategory("full_movie", "Långfilm", listOf("full movie")),
                Subcategory("amateur", "Amatör", listOf("amateur")),
                Subcategory("reality", "Verklighetsnära", listOf("reality")),
                Subcategory("vintage", "Retro & vintage", listOf("vintage", "retro")),
                Subcategory("compilation", "Samlingar", listOf("compilation"))
            )
        ),
        Category(
            id = "fetish",
            label = "Fetisch & tillbehör",
            emoji = "⛓️",
            description = "BDSM, fotfetisch och liknande",
            subcategories = listOf(
                Subcategory("bdsm", "BDSM", listOf("bdsm")),
                Subcategory("bondage", "Bondage", listOf("bondage")),
                Subcategory("feet", "Fotfetisch", listOf("feet fetish", "feet")),
                Subcategory("spanking", "Spanking", listOf("spanking")),
                Subcategory("gloves", "Handskar", listOf("gloves")),
                Subcategory("tickling", "Tickling", listOf("tickling")),
                Subcategory("toys", "Leksaker", listOf("sex toys")),
                Subcategory("blindfold", "Ögonbindel", listOf("blindfold"))
            )
        ),
        Category(
            id = "finish",
            label = "Avslutning",
            emoji = "💦",
            description = "Hur scenen slutar",
            subcategories = listOf(
                Subcategory("creampie", "Creampie", listOf("creampie")),
                Subcategory("cumshot", "Cumshot", listOf("cumshot")),
                Subcategory("facial", "Facial", listOf("facial")),
                Subcategory("swallow", "Swallow", listOf("swallow")),
                Subcategory("squirting", "Squirting", listOf("squirting")),
                Subcategory("multiple", "Flera avslut", listOf("multiple orgasms"))
            )
        )
    )

    val allSubcategories: List<Subcategory> = categories.flatMap { it.subcategories }

    private val categoryIndex: Map<String, Category> = categories.associateBy { it.id }
    private val subcategoryIndex: Map<String, Subcategory> = allSubcategories.associateBy { it.id }

    fun category(id: String): Category? = categoryIndex[id]

    fun subcategory(id: String): Subcategory? = subcategoryIndex[id]

    fun subcategoriesFor(categoryId: String): List<Subcategory> =
        categoryIndex[categoryId]?.subcategories.orEmpty()

    /** Alla söktermer för valda underkategorier, i stabil ordning. */
    fun termsFor(subcategoryIds: Collection<String>): List<String> =
        subcategoryIds.mapNotNull { subcategoryIndex[it] }
            .flatMap { it.terms }
            .distinct()

    fun labelsFor(subcategoryIds: Collection<String>): List<String> =
        subcategoryIds.mapNotNull { subcategoryIndex[it]?.label }

    fun categoryIdsFor(subcategoryIds: Collection<String>): Set<String> =
        subcategoryIds.mapNotNull { subcategoryIndex[it] }
            .mapNotNull { sub -> categories.firstOrNull { category -> category.subcategories.any { it.id == sub.id } }?.id }
            .toSet()
}
