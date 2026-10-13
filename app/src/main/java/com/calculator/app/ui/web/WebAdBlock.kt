package com.calculator.app.ui.web

import com.calculator.app.network.AdBlockHosts
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale

/** Vad webbläsaren ska göra med en navigering. */
enum class NavigationDecision {
    /** Ladda i samma WebView. */
    ALLOW,

    /** Känd annons-/popunderdomän – ignorera helt. */
    BLOCK,

    /** Annan sajt eller app-schema – öppna utanför appen i stället. */
    EXTERNAL
}

/** Hur hård blockeringen ska vara. */
enum class AdBlockLevel(val id: String, val label: String) {
    STANDARD("standard", "Standard (rekommenderad)"),
    STRICT("strict", "Strikt – blockerar allt externt"),
    OFF("off", "Av");

    companion object {
        fun fromId(id: String?): AdBlockLevel = entries.firstOrNull { it.id == id } ?: STANDARD
    }
}

/**
 * All reklamblockeringslogik för WebView-spelaren. Ren Kotlin (utom
 * [injectionScript] som bara bygger en sträng) så att reglerna kan enhetstestas.
 */
object WebAdBlock {

    private val mediaHosts = listOf(
        ".m3u8", ".mp4", ".webm", ".ts", ".mpd", ".vtt"
    )

    /** True om själva förfrågan aldrig får gå till nätet. */
    fun isBlockedRequest(url: String?, level: AdBlockLevel = AdBlockLevel.STANDARD, pageUrl: String? = null): Boolean {
        val parsed = url?.toHttpUrlOrNull() ?: return true
        if (!parsed.isHttps) return true
        if (level == AdBlockLevel.OFF) return false
        if (level == AdBlockLevel.STRICT && pageUrl != null &&
            !sameSite(pageUrl.toHttpUrlOrNull()?.host.orEmpty(), parsed.host)) return true
        if (AdBlockHosts.isBlocked(parsed.host)) return true
        if (AdBlockHosts.looksLikeAdPath(parsed.encodedPath + "?" + parsed.encodedQuery)) return true
        return false
    }

    /**
     * Vad som ska hända när spelaren vill navigera. Samma sajt får laddas,
     * annonsdomäner blockeras och allt annat lämnar appen.
     */
    fun navigationFor(
        currentUrl: String?,
        targetUrl: String?,
        level: AdBlockLevel = AdBlockLevel.STANDARD
    ): NavigationDecision {
        val target = targetUrl ?: return NavigationDecision.BLOCK

        if (target.startsWith("http://", ignoreCase = true)) return NavigationDecision.BLOCK
        if (!target.startsWith("https://", ignoreCase = true)) {
            val scheme = target.substringBefore(':').lowercase(Locale.ROOT)
            return when {
                scheme == "about" || scheme == "data" || scheme == "blob" || scheme == "javascript" ->
                    NavigationDecision.BLOCK
                scheme in AdBlockHosts.externalSchemes -> NavigationDecision.BLOCK
                else -> NavigationDecision.EXTERNAL
            }
        }

        val parsedTarget = target.toHttpUrlOrNull() ?: return NavigationDecision.BLOCK
        val currentHost = currentUrl?.toHttpUrlOrNull()?.host.orEmpty().lowercase(Locale.ROOT)
        val targetHost = parsedTarget.host.lowercase(Locale.ROOT)

        // Annonsvärdar och typiska popunder-vägar stoppas oavsett var de ligger.
        if (level != AdBlockLevel.OFF && isBlockedRequest(target, level)) return NavigationDecision.BLOCK
        if (level == AdBlockLevel.OFF) return NavigationDecision.ALLOW

        return when {
            sameSite(currentHost, targetHost) -> NavigationDecision.ALLOW
            level == AdBlockLevel.STRICT -> NavigationDecision.BLOCK
            isMedia(target) -> NavigationDecision.ALLOW
            else -> NavigationDecision.EXTERNAL
        }
    }

    /** Sant om båda hör till samma registrerbara domän (t.ex. xvideos.com och cdn.xvideos.com). */
    fun sameSite(currentHost: String, targetHost: String): Boolean {
        if (currentHost.isBlank() || targetHost.isBlank()) return false
        if (currentHost == targetHost) return true
        return registrable(currentHost) == registrable(targetHost)
    }

    fun isMedia(url: String): Boolean {
        val path = url.toHttpUrlOrNull()?.encodedPath?.lowercase(Locale.ROOT) ?: return false
        return mediaHosts.any { path.endsWith(it) }
    }

    private fun registrable(host: String): String =
        "https://$host".toHttpUrlOrNull()?.let { it.topPrivateDomain() ?: it.host }.orEmpty()

    /** CSS som gömmer de vanligaste annons- och popunder-elementen. */
    val hideAdsCss: String = """
        ins.adsbygoogle, ins[data-ad-client], ins[data-ad-slot],
        iframe[src*="doubleclick"], iframe[src*="googlesyndication"], iframe[src*="exoclick"],
        iframe[src*="juicyads"], iframe[src*="trafficjunky"], iframe[src*="adsterra"],
        iframe[src*="popads"], iframe[src*="propellerads"], iframe[src*="clickadu"],
        iframe[id*="ad-"], iframe[class*="ad-"], iframe[name*="google_ads"],
        a[href*="exoclick"], a[href*="juicyads"], a[href*="trafficjunky"], a[href*="adsterra"],
        a[href*="propellerads"], a[href*="popads"], a[href*="clickadu"], a[href*="tsyndicate"],
        [id*="popunder"], [class*="popunder"], [class*="pop-under"],
        [class*="ad-overlay"], [id*="ad-overlay"], [class*="adblock-modal"],
        [class*="push-notification"], [id*="push-notification"],
        .ad, .ads, .advert, .advertisement, .adsbygoogle, .banner-ad, .banner_ads,
        div[id^="ad-"], div[id^="ads-"], div[class^="ad-"], div[class^="ads-"],
        div[id$="-ad"], div[class$="-ad"], [data-ad], [data-ad-slot], [data-ad-client],
        [aria-label="Advertisement"]
        { display: none !important; visibility: hidden !important; pointer-events: none !important; }
    """.trimIndent()

    /**
     * JavaScript som injiceras i varje sida: stänger fönsteröppningar, tar bort
     * annonsnoder, ser till att videon får kontroller och exponerar små hjälpfunktioner
     * som spelarens knappar använder (spola, pausa, läsa position).
     */
    fun injectionScript(level: AdBlockLevel = AdBlockLevel.STANDARD): String = buildString {
        append("(function(){")
        append("if(window.__pcInstalled){return;}window.__pcInstalled=true;")
        append("var LEVEL='").append(level.id).append("';")
        // 1) Döda popups och popunders.
        append("try{window.open=function(){return null;};")
        append("window.alert=function(){};")
        append("document.addEventListener('click',function(e){")
        append("var a=e.target&&e.target.closest?e.target.closest('a[target=\"_blank\"]'):null;")
        append("if(a){a.removeAttribute('target');a.setAttribute('rel','noopener noreferrer');}")
        append("},true);}catch(e){}")
        if (level != AdBlockLevel.OFF) {
        // 2) Göm annonser med CSS och städa bort dem löpande.
        append("try{var css=").append(jsString(hideAdsCss)).append(";")
        append("var add=function(){var s=document.createElement('style');s.type='text/css';")
        append("s.appendChild(document.createTextNode(css));(document.head||document.documentElement).appendChild(s);};")
        append("add();")
        append("var sweep=function(){try{")
        append("var nodes=document.querySelectorAll('iframe,ins,[id*=\"popunder\"],[class*=\"popunder\"],a[href*=\"exoclick\"],a[href*=\"juicyads\"],a[href*=\"trafficjunky\"],a[href*=\"adsterra\"],a[href*=\"onclickads\"],[class*=\"ad-overlay\"]');")
        append("for(var i=0;i<nodes.length;i++){var n=nodes[i];if(!n||!n.parentNode){continue;}")
        append("var src=(n.getAttribute&&(n.getAttribute('src')||n.getAttribute('href'))||'').toLowerCase();")
        append("var idc=((n.id||'')+(n.className&&n.className.baseVal?n.className.baseVal:(n.className||''))).toLowerCase();")
        append("if(/exoclick|juicyads|trafficjunky|adsterra|popads|propellerads|clickadu|doubleclick|googlesyndication|onclickads|popunder|ad-overlay/.test(src+idc)){n.parentNode.removeChild(n);}")
        append("}}catch(e){}};")
        append("setTimeout(sweep,800);setTimeout(sweep,2500);setTimeout(sweep,6000);")
        append("setTimeout(add,1500);}catch(e){}")
        }
        // 3) Videohjälp: egna kontroller + spolning + position.
        append("window.__pcVideo=function(){try{")
        append("var list=document.querySelectorAll('video');var best=null;")
        append("for(var i=0;i<list.length;i++){var v=list[i];var d=isFinite(v.duration)?v.duration:0;")
        append("if(!best||d>(isFinite(best.duration)?best.duration:0)){best=v;}}return best;}catch(e){return null;}};")
        append("window.__pcState=function(){try{var v=window.__pcVideo();if(!v){return '{}';}")
        append("return JSON.stringify({t:v.currentTime||0,d:isFinite(v.duration)?v.duration:0,p:!!v.paused,vw:v.videoWidth||0,vh:v.videoHeight||0,m:!!v.muted});}catch(e){return '{}';}};")
        append("window.__pcSeekBy=function(s){try{var v=window.__pcVideo();if(!v){return 'no';}")
        append("var d=isFinite(v.duration)?v.duration:0;var t=(v.currentTime||0)+s;if(d>0){t=Math.max(0,Math.min(d-0.5,t));}")
        append("v.currentTime=Math.max(0,t);return 'ok';}catch(e){return 'err';}};")
        append("window.__pcToggle=function(){try{var v=window.__pcVideo();if(!v){return 'no';}")
        append("if(v.paused){v.play();}else{v.pause();}return 'ok';}catch(e){return 'err';}};")
        append("window.__pcControls=function(on){try{var v=window.__pcVideo();if(!v){return 'no';}")
        append("v.controls=!!on;if(on){v.setAttribute('controls','controls');}else{v.removeAttribute('controls');}return 'ok';}catch(e){return 'err';}};")
        append("window.__pcResume=function(seconds){try{window.__pcResumeAt=(seconds>15)?seconds:0;return 'ok';}catch(e){return 'err';}};")
        append("try{setInterval(function(){var v=window.__pcVideo();if(!v||!window.__pcResumeAt){return;}")
        append("var d=isFinite(v.duration)?v.duration:0;if(d>window.__pcResumeAt+20&&(v.currentTime||0)<5){v.currentTime=window.__pcResumeAt;window.__pcResumeAt=0;}},1200);}catch(e){}")
        // 4) Låt videon spela utan extra klick och i full bredd.
        append("try{var fix=function(){var v=window.__pcVideo();if(!v){return;}")
        append("v.setAttribute('playsinline','');v.setAttribute('webkit-playsinline','');")
        append("if(!v.hasAttribute('controls')){v.setAttribute('controls','controls');}};")
        append("setTimeout(fix,1200);setTimeout(fix,3000);}catch(e){}")
        append("})();")
    }

    /** Gör om en sträng till ett säkert JavaScript-strängliteral. */
    fun jsString(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", " ")
            .replace("\r", " ")
        return "\"$escaped\""
    }
}
