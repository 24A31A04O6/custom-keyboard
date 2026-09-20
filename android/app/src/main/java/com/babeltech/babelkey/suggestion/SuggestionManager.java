package com.babeltech.babelkey.suggestion;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.babeltech.babelkey.R;
import com.babeltech.babelkey.core.ServiceCallback;
import com.babeltech.babelkey.translate.TranslationService;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * SuggestionManager — owns all dictionaries, autocorrect, translate, grammar,
 * AI replies, stats, quick phrases, text shortcuts, and word learning.
 */
public class SuggestionManager implements SuggestionView.SuggestionClickListener {
    private static final String TAG = "SuggestionManager";
    private static final String SEP = "|||";

    public final Map<String, List<String>> DICT        = new HashMap<>();
    public final Map<String, List<String>> TEL_ENG_DICT = new HashMap<>();
    public final Map<String, String> TRANSLATE_DICT    = new HashMap<>();
    public final Map<String, String> AUTOCORRECT_DICT  = new HashMap<>();
    public final Map<String, String> EMOJI_SUGGESTIONS = new HashMap<>();
    public static final Map<String, String> GRAMMAR_DICT = new HashMap<>();
    static {
        GRAMMAR_DICT.put("nenu veltru","nenu veltharu"); GRAMMAR_DICT.put("nenu chustru","nenu chustharu");
        GRAMMAR_DICT.put("nenu chestru","nenu chestharu"); GRAMMAR_DICT.put("nenu vastru","nenu vastharu");
        GRAMMAR_DICT.put("nenu istru","nenu istharu"); GRAMMAR_DICT.put("meeru veltru","meeru veltharu");
        GRAMMAR_DICT.put("meeru chustru","meeru chustharu"); GRAMMAR_DICT.put("vaadu veltru","vaadu velthadu");
        GRAMMAR_DICT.put("vaadu chustru","vaadu chustadu"); GRAMMAR_DICT.put("aame veltru","aame velthundi");
        GRAMMAR_DICT.put("na peru","naa peru"); GRAMMAR_DICT.put("na illu","naa illu");
        GRAMMAR_DICT.put("na pani","naa pani"); GRAMMAR_DICT.put("na naanna","naa naanna");
    }

    private static final String PREF_LEARNED="learnedWords", PREF_PERSONAL="personalDict";
    private static final String PREF_STATS_W="statsWords", PREF_STATS_D="statsDaily";
    private static final String PREF_SHORTCUTS="textShortcuts", PREF_PHRASES="quickPhrases";

    private final Context ctx;
    private final SharedPreferences prefs;
    private final ServiceCallback cb;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public final StringBuilder currentWord = new StringBuilder();
    private String lastOrig=null, lastCorr=null;
    private boolean canUndo=false;
    private Set<String> learnedWords=new HashSet<>(), personalDict=new HashSet<>();
    private Map<String,String>  shortcuts=new HashMap<>();
    private List<String>        phrases=new ArrayList<>();
    private Map<String,Integer> corrStats=new HashMap<>(), dailyStats=new HashMap<>();
    private boolean dark=true, aiEnabled=true;
    // ML Kit Smart Reply
    private final SmartReplyManager smartReplyManager;

    private SuggestionView sugView;
    private LinearLayout sugContainer, statsContainer, aiContainer, phrasesContainer;
    private ScrollView statsPanel;
    private View grammarBanner, aiPanel, phrasesPanel;
    private TextView grammarText;

    public SuggestionManager(Context ctx, SharedPreferences prefs, ServiceCallback cb) {
        this.ctx=ctx; this.prefs=prefs; this.cb=cb;
        smartReplyManager = new SmartReplyManager();
    }

    public void loadAll() {
        loadTransDict(); loadKVDict(R.raw.autocorrect_dict,AUTOCORRECT_DICT,"AUTOCORRECT");
        loadKVDict(R.raw.emoji_suggestions,EMOJI_SUGGESTIONS,"EMOJI");
        loadPrefixDict(R.raw.suggestions_dict,DICT,"DICT");
        loadPrefixDict(R.raw.tel_eng_dict,TEL_ENG_DICT,"TEL_ENG");
        learnedWords=new HashSet<>(prefs.getStringSet(PREF_LEARNED,new HashSet<>()));
        personalDict=new HashSet<>(prefs.getStringSet(PREF_PERSONAL,new HashSet<>()));
        loadShortcuts(); loadPhrases(); loadStats();
    }

    public void attachViews(LinearLayout sugContainer, boolean dark,
            ScrollView statsPanel, LinearLayout statsContainer,
            View grammarBanner, TextView grammarText,
            View aiPanel, LinearLayout aiContainer,
            View phrasesPanel, LinearLayout phrasesContainer) {
        this.sugContainer=sugContainer; this.dark=dark;
        this.statsPanel=statsPanel; this.statsContainer=statsContainer;
        this.grammarBanner=grammarBanner; this.grammarText=grammarText;
        this.aiPanel=aiPanel; this.aiContainer=aiContainer;
        this.phrasesPanel=phrasesPanel; this.phrasesContainer=phrasesContainer;
        sugView=new SuggestionView(sugContainer, this, dark);
    }

    public void onThemeChanged(boolean d) { dark=d; }
    public void setAiEnabled(boolean e) { aiEnabled=e; }
    public boolean isAiEnabled() { return aiEnabled; }

    private boolean isAlphabeticLayer = true;

    public void setLayer(int mode) {
        this.isAlphabeticLayer = (mode == MyKeyboardService.MODE_QWERTY);
        if (!isAlphabeticLayer) {
            currentWord.setLength(0);
            clearSuggestions();
        }
    }

    public boolean isAlphabeticLayer() {
        return isAlphabeticLayer;
    }

    // Suggestions
    public void updateSuggestions() {
        if(sugView==null) return;
        if(!isAlphabeticLayer) { sugView.clear(); return; }
        if(currentWord.length()==0){sugView.clear();return;}
        String prefix=currentWord.toString().toLowerCase();
        List<String> res=new ArrayList<>();
        String emo=EMOJI_SUGGESTIONS.get(prefix);
        if(emo!=null){List<String>c=new ArrayList<>(Arrays.asList(emo.split(" ")));sugView.setSuggestions(c);return;}

        // 1. Check Tel-Eng Romanized dictionary (e.g. nen -> nenu, nuv -> nuvvu, ela -> ela)
        List<String> telMatches = TEL_ENG_DICT.get(prefix);
        if (telMatches != null) {
            for (String w : telMatches) {
                if (!res.contains(w)) {
                    res.add(w);
                    if (res.size() >= 3) break;
                }
            }
        }

        // 2. Check English dictionary
        if(prefix.length()>=1){
            for(Map.Entry<String,List<String>>e:DICT.entrySet()){
                if(prefix.startsWith(e.getKey())||e.getKey().startsWith(prefix)){
                    for(String w:e.getValue()) {
                        if(w.startsWith(prefix)&&!res.contains(w)){
                            res.add(w);
                            if(res.size()>=5) break;
                        }
                    }
                }
                if(res.size()>=5) break;
            }
        }

        // 3. Personal dictionary
        for(String w:personalDict){
            if(w.toLowerCase().startsWith(prefix)&&!res.contains(w)){
                res.add(w);
                if(res.size()>=5) break;
            }
        }

        if(!res.isEmpty())sugView.setSuggestions(res);else sugView.clear();
    }

    public void showSwipeSuggestions(List<String> c) { if(sugView!=null&&!c.isEmpty())sugView.setSuggestions(c); else if(!c.isEmpty()){InputConnection ic=cb.getInputConnection();if(ic!=null)ic.commitText(c.get(0)+" ",1);currentWord.setLength(0);} }
    public void clearSuggestions() { if(sugView!=null) sugView.clear(); }

    @Override public void onSuggestionClicked(String word, int index) {
        InputConnection ic=cb.getInputConnection(); if(ic==null) return;
        int len=currentWord.length(); if(len>0) ic.deleteSurroundingText(len,0);
        ic.commitText(word+" ",1); currentWord.setLength(0); clearSuggestions();
    }

    // Translate
    public void handleTranslate() {
        InputConnection ic=cb.getInputConnection(); if(ic==null) return;
        CharSequence bef=ic.getTextBeforeCursor(500,0); if(bef==null||bef.length()==0){Toast.makeText(ctx,"Type a word or sentence first to translate",Toast.LENGTH_SHORT).show();return;}
        String full=bef.toString(); int ss=0; for(int i=full.length()-1;i>=0;i--){char c=full.charAt(i);if(c=='.'||c=='!'||c=='?'||c=='\n'){ss=i+1;break;}}
        String sent=full.substring(ss); if(sent.trim().isEmpty()){Toast.makeText(ctx,"Type a word or sentence first to translate",Toast.LENGTH_SHORT).show();return;}
        
        Toast.makeText(ctx, "Translating...", Toast.LENGTH_SHORT).show();
        
        TranslationService.translateTeluguToEnglish(sent, new TranslationService.TranslationCallback() {
            @Override
            public void onSuccess(String translatedText) {
                applyTranslation(sent, translatedText, ic);
            }

            @Override
            public void onFailure(String error) {
                // Fallback to local dictionary translation
                StringBuilder tr=new StringBuilder(); int i=0;
                while(i<sent.length()){char ch=sent.charAt(i);if(Character.isLetter(ch)){int ws=i;while(i<sent.length()&&Character.isLetter(sent.charAt(i)))i++;String w=sent.substring(ws,i);String tw=TRANSLATE_DICT.get(w.toLowerCase());if(tw!=null){if(Character.isUpperCase(w.charAt(0)))tw=Character.toUpperCase(tw.charAt(0))+tw.substring(1);tr.append(tw);}else tr.append(w);}else{tr.append(ch);i++;}}
                String res=tr.toString();
                if(!res.equals(sent)){
                    applyTranslation(sent, res, ic);
                } else {
                    Toast.makeText(ctx,"Translation failed: " + error,Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void applyTranslation(String origSent, String translated, InputConnection ic) {
        lastOrig=origSent; lastCorr=translated; canUndo=true;
        ic.deleteSurroundingText(origSent.length(),0);
        ic.commitText(translated,1);
        recordCorrection(origSent);
        Toast.makeText(ctx,"Translated!",Toast.LENGTH_SHORT).show();
        currentWord.setLength(0); clearSuggestions();
    }

    // AutoCorrect
    public void handleAutoCorrect() {
        if (!isAlphabeticLayer) return;
        InputConnection ic=cb.getInputConnection(); if(ic==null) return;
        CharSequence bef=ic.getTextBeforeCursor(500,0); if(bef==null||bef.length()==0) return;
        String full=bef.toString(); int ss=0; for(int i=full.length()-1;i>=0;i--){char c=full.charAt(i);if(c=='.'||c=='!'||c=='?'||c=='\n'){ss=i+1;break;}}
        String sent=full.substring(ss); if(sent.trim().isEmpty()) return;
        StringBuilder corr=new StringBuilder(); List<String> cw=new ArrayList<>(); int i=0;
        while(i<sent.length()){char ch=sent.charAt(i);if(Character.isLetter(ch)){int ws=i;while(i<sent.length()&&Character.isLetter(sent.charAt(i)))i++;String w=sent.substring(ws,i),wl=w.toLowerCase();if(learnedWords.contains(wl)||personalDict.contains(wl)){corr.append(w);continue;}String rep=AUTOCORRECT_DICT.get(wl);if(rep!=null){if(Character.isUpperCase(w.charAt(0)))rep=Character.toUpperCase(rep.charAt(0))+rep.substring(1);corr.append(rep);cw.add(w);}else corr.append(w);}else{corr.append(ch);i++;}}
        String cs=corr.toString();
        if(!cs.equals(sent)){lastOrig=sent;lastCorr=cs;canUndo=true;ic.deleteSurroundingText(sent.length(),0);ic.commitText(cs,1);for(String w:cw)recordCorrection(w);}
        currentWord.setLength(0); clearSuggestions();
    }

    // Undo
    public void handleUndo() {
        if(!canUndo||lastOrig==null||lastCorr==null) return;
        InputConnection ic=cb.getInputConnection(); if(ic==null) return;
        CharSequence bef=ic.getTextBeforeCursor(lastCorr.length(),0);
        if(bef!=null&&bef.toString().equals(lastCorr)){ic.deleteSurroundingText(lastCorr.length(),0);ic.commitText(lastOrig,1);}
        canUndo=false;lastOrig=null;lastCorr=null;currentWord.setLength(0);clearSuggestions();
    }

    // Shortcut
    public boolean checkShortcut(InputConnection ic) {
        if (!isAlphabeticLayer) return false;
        if(ic==null||currentWord.length()==0) return false;
        String e=shortcuts.get(currentWord.toString().toLowerCase()); if(e==null) return false;
        ic.deleteSurroundingText(currentWord.length(),0); ic.commitText(e+" ",1); currentWord.setLength(0); clearSuggestions(); return true;
    }

    // Learning
    public void learnWord() {
        if (!isAlphabeticLayer) return;
        if(currentWord.length()==0) return; String w=currentWord.toString().toLowerCase(); if(AUTOCORRECT_DICT.containsKey(w)&&!learnedWords.contains(w)){learnedWords.add(w);prefs.edit().putStringSet(PREF_LEARNED,learnedWords).apply();}
    }
    public void addToPersonalDict(String w) { if(w==null||w.isEmpty())return; personalDict.add(w.toLowerCase()); prefs.edit().putStringSet(PREF_PERSONAL,personalDict).apply(); }

    // Grammar
    public void checkGrammar(InputConnection ic) {
        if(ic==null||grammarBanner==null) return;
        CharSequence bef=ic.getTextBeforeCursor(100,0); if(bef==null) return;
        String text=bef.toString().toLowerCase().trim();
        for(Map.Entry<String,String>e:GRAMMAR_DICT.entrySet()){if(text.contains(e.getKey())){String s=e.getValue();handler.post(()->{if(grammarText!=null)grammarText.setText(s);if(grammarBanner!=null)grammarBanner.setVisibility(View.VISIBLE);});return;}}
    }
    public void hideGrammarBanner() { if(grammarBanner!=null) grammarBanner.setVisibility(View.GONE); }

    // AI replies  powered by ML Kit Smart Reply
    public void updateAiReplies(InputConnection ic) {
        if (!aiEnabled || aiPanel == null || aiContainer == null) return;
        if (ic == null) { aiPanel.setVisibility(View.GONE); return; }
        CharSequence bef = ic.getTextBeforeCursor(200, 0);
        String context = bef != null ? bef.toString().trim() : "";
        if (context.isEmpty()) { aiPanel.setVisibility(View.GONE); return; }

        // Ask ML Kit asynchronously; callback fires on main thread
        smartReplyManager.suggest(context, replies -> showAiReplies(replies));
    }

    /** Renders the quick-reply chip strip. Must be called on the main thread. */
    private void showAiReplies(List<String> replies) {
        if (aiPanel == null || aiContainer == null) return;
        if (replies == null || replies.isEmpty()) { aiPanel.setVisibility(View.GONE); return; }
        aiContainer.removeAllViews();
        for (String r : replies) {
            Button btn = new Button(ctx);
            btn.setText(r);
            btn.setTextSize(11f);
            btn.setTextColor(dark ? Color.WHITE : androidx.core.content.ContextCompat.getColor(ctx, R.color.key_text_color));
            btn.setBackgroundColor(dark ? androidx.core.content.ContextCompat.getColor(ctx, R.color.ai_chip_background) : androidx.core.content.ContextCompat.getColor(ctx, R.color.ai_chip_background));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(4, 2, 4, 2);
            btn.setLayoutParams(lp);
            btn.setPadding(12, 0, 12, 0);
            btn.setOnClickListener(v -> {
                InputConnection c = cb.getInputConnection();
                if (c != null) c.commitText(r + " ", 1);
            });
            aiContainer.addView(btn);
        }
        aiPanel.setVisibility(View.VISIBLE);
    }

    // Phrases
    public void togglePhrases() { if(phrasesPanel==null) return; boolean v=phrasesPanel.getVisibility()==View.VISIBLE; phrasesPanel.setVisibility(v?View.GONE:View.VISIBLE); if(!v) refreshPhrases(); }
    public void refreshPhrases() {
        if(phrasesContainer==null) return; phrasesContainer.removeAllViews();
        for(String ph:phrases){Button btn=new Button(ctx);btn.setText(ph);btn.setTextSize(12f);btn.setTextColor(dark?Color.WHITE:androidx.core.content.ContextCompat.getColor(ctx, R.color.key_text_color));btn.setBackgroundColor(dark?androidx.core.content.ContextCompat.getColor(ctx, R.color.phrases_panel_background):androidx.core.content.ContextCompat.getColor(ctx, R.color.phrases_panel_background));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.WRAP_CONTENT);lp.setMargins(4,2,4,2);btn.setLayoutParams(lp);btn.setPadding(16,0,16,0);btn.setOnClickListener(v->{InputConnection ic=cb.getInputConnection();if(ic!=null)ic.commitText(ph+" ",1);});btn.setOnLongClickListener(v->{phrases.remove(ph);savePhrases();refreshPhrases();Toast.makeText(ctx,"Phrase removed",Toast.LENGTH_SHORT).show();return true;});phrasesContainer.addView(btn);}
    }
    private void savePhrases() { StringBuilder sb=new StringBuilder();for(String s:phrases)sb.append(s).append(SEP);prefs.edit().putString(PREF_PHRASES,sb.toString()).apply(); }
    private void loadPhrases() { phrases.clear();String r=prefs.getString(PREF_PHRASES,"");if(!r.isEmpty())for(String s:r.split(Pattern.quote(SEP)))if(!s.isEmpty())phrases.add(s); }

    // Shortcuts
    public void saveShortcuts() { StringBuilder sb=new StringBuilder();for(Map.Entry<String,String>e:shortcuts.entrySet())sb.append(e.getKey()).append("=").append(e.getValue()).append(SEP);prefs.edit().putString(PREF_SHORTCUTS,sb.toString()).apply(); }
    private void loadShortcuts() { shortcuts.clear();String r=prefs.getString(PREF_SHORTCUTS,"");if(r.isEmpty())return;for(String e:r.split(Pattern.quote(SEP))){String[]p=e.split("=");if(p.length==2&&!p[0].isEmpty())shortcuts.put(p[0].toLowerCase(),p[1]);} }
    public Map<String,String> getShortcuts() { return shortcuts; }

    // Stats
    public void toggleStats() { if(statsPanel==null) return; boolean v=statsPanel.getVisibility()==View.VISIBLE; statsPanel.setVisibility(v?View.GONE:View.VISIBLE); if(!v) refreshStats(); }
    private void refreshStats() {
        if(statsContainer==null) return; statsContainer.removeAllViews();
        addRow("=== Correction Stats ===",true); for(Map.Entry<String,Integer>e:corrStats.entrySet()) addRow(e.getKey()+": "+e.getValue()+"x",false);
        addRow("=== Daily Stats ===",true); for(Map.Entry<String,Integer>e:dailyStats.entrySet()) addRow(e.getKey()+": "+e.getValue()+" words",false);
    }
    private void addRow(String text,boolean bold) { TextView tv=new TextView(ctx);tv.setText(text);tv.setTextSize(12f);if(bold)tv.setTypeface(null,android.graphics.Typeface.BOLD);tv.setTextColor(dark?Color.WHITE:androidx.core.content.ContextCompat.getColor(ctx, R.color.key_text_color));tv.setPadding(8,2,8,2);if(statsContainer!=null)statsContainer.addView(tv); }
    private void recordCorrection(String orig) { if(orig==null||orig.isEmpty()) return; String k=orig.toLowerCase();corrStats.put(k,corrStats.getOrDefault(k,0)+1);String today=new SimpleDateFormat("yyyy-MM-dd",Locale.getDefault()).format(new Date());dailyStats.put(today,dailyStats.getOrDefault(today,0)+1);saveStats(); }
    private void saveStats() { StringBuilder w=new StringBuilder(),d=new StringBuilder();for(Map.Entry<String,Integer>e:corrStats.entrySet())w.append(e.getKey()).append("=").append(e.getValue()).append(SEP);for(Map.Entry<String,Integer>e:dailyStats.entrySet())d.append(e.getKey()).append("=").append(e.getValue()).append(SEP);prefs.edit().putString(PREF_STATS_W,w.toString()).putString(PREF_STATS_D,d.toString()).apply(); }
    private void loadStats() { corrStats.clear();String ws=prefs.getString(PREF_STATS_W,"");if(!ws.isEmpty())for(String e:ws.split(Pattern.quote(SEP))){String[]p=e.split("=");if(p.length==2)try{corrStats.put(p[0],Integer.parseInt(p[1]));}catch(Exception ig){}} dailyStats.clear();String ds=prefs.getString(PREF_STATS_D,"");if(!ds.isEmpty())for(String e:ds.split(Pattern.quote(SEP))){String[]p=e.split("=");if(p.length==2)try{dailyStats.put(p[0],Integer.parseInt(p[1]));}catch(Exception ig){}} }

    public String getLastWord(InputConnection ic) { if(ic==null) return null;CharSequence bef=ic.getTextBeforeCursor(50,0);if(bef==null||bef.length()==0)return null;String t=bef.toString();int e=t.length(),s=e;while(s>0&&!Character.isWhitespace(t.charAt(s-1)))s--;if(s==e)return null;return t.substring(s,e); }

    // Dict loaders
    private void loadTransDict() {
        TRANSLATE_DICT.clear(); InputStream is=null;
        try{is=ctx.getResources().openRawResource(R.raw.translations);BufferedReader r=new BufferedReader(new InputStreamReader(is,StandardCharsets.UTF_8));StringBuilder sb=new StringBuilder();String l;while((l=r.readLine())!=null)sb.append(l);r.close();String json=sb.toString();if(json.trim().isEmpty())return;JSONObject o=new JSONObject(json);java.util.Iterator<String>ks=o.keys();while(ks.hasNext()){String k=ks.next();try{String v=o.getString(k);if(k!=null&&!k.trim().isEmpty()&&v!=null&&!v.trim().isEmpty())TRANSLATE_DICT.put(k.trim().toLowerCase(),v.trim());}catch(JSONException ig){}}Log.i(TAG,"Trans: "+TRANSLATE_DICT.size());}catch(Exception e){Log.e(TAG,"Trans error",e);}finally{if(is!=null)try{is.close();}catch(IOException ig){}}
    }
    private void loadKVDict(int res, Map<String,String> target, String name) {
        target.clear(); InputStream is=null;
        try{is=ctx.getResources().openRawResource(res);BufferedReader r=new BufferedReader(new InputStreamReader(is,StandardCharsets.UTF_8));StringBuilder sb=new StringBuilder();String l;while((l=r.readLine())!=null)sb.append(l);r.close();String json=sb.toString();if(json.trim().isEmpty())return;JSONObject o=new JSONObject(json);java.util.Iterator<String>ks=o.keys();while(ks.hasNext()){String k=ks.next();try{String v=o.getString(k);if(k!=null&&!k.trim().isEmpty()&&v!=null&&!v.trim().isEmpty())target.put(k.trim().toLowerCase(),v.trim());}catch(JSONException ig){}}Log.i(TAG,name+": "+target.size());}catch(Exception e){Log.e(TAG,name+" error",e);}finally{if(is!=null)try{is.close();}catch(IOException ig){}}
    }
    private void loadPrefixDict(int res, Map<String,List<String>> target, String name) {
        target.clear(); InputStream is=null;
        try{is=ctx.getResources().openRawResource(res);BufferedReader r=new BufferedReader(new InputStreamReader(is,StandardCharsets.UTF_8));StringBuilder sb=new StringBuilder();String l;while((l=r.readLine())!=null)sb.append(l);r.close();String json=sb.toString();if(json.trim().isEmpty())return;JSONObject o=new JSONObject(json);java.util.Iterator<String>ks=o.keys();int cnt=0;while(ks.hasNext()){String k=ks.next();try{org.json.JSONArray arr=o.getJSONArray(k);List<String>vals=new ArrayList<>();for(int i=0;i<arr.length();i++){String v=arr.optString(i,null);if(v!=null&&!v.trim().isEmpty())vals.add(v.trim());}if(k!=null&&!k.trim().isEmpty()&&!vals.isEmpty()){target.put(k.trim().toLowerCase(),vals);cnt++;}}catch(JSONException ig){}}Log.i(TAG,name+": "+cnt+" entries");}catch(Exception e){Log.e(TAG,name+" error",e);}finally{if(is!=null)try{is.close();}catch(IOException ig){}}
    }

    public void detach() { sugContainer=null;statsPanel=null;statsContainer=null;grammarBanner=null;grammarText=null;aiPanel=null;aiContainer=null;phrasesPanel=null;phrasesContainer=null;sugView=null; smartReplyManager.close(); }
}
