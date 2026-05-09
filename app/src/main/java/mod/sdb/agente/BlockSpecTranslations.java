package mod.sdb.agente;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ArrayList;

/**
 * Translates block "spec" display strings to PT-BR when device locale is Portuguese.
 * Safe: only affects display labels. Logic (code/name/opCode) is never touched.
 */
public class BlockSpecTranslations {

    private static final HashMap<String, String> PT_SPECS = new HashMap<>();

    static {
        // View / Click events
        PT_SPECS.put("viewOnClick",                   "Quando %m.view clicado");
        PT_SPECS.put("viewOnLongClick",               "Quando %m.view pressionado longo");
        PT_SPECS.put("viewOnTouch",                   "Quando %m.view tocado");
        PT_SPECS.put("checkboxOnChecked",             "Quando %m.checkbox marcado");
        PT_SPECS.put("checkboxIsChecked",             "estáMarcado");
        PT_SPECS.put("performClick",                  "%m.view executarClique");
        PT_SPECS.put("onSwipeRefreshLayout",          "Quando %m.swiperefreshlayout atualizado");

        // Command blocks
        PT_SPECS.put("CommandBlockJava",              "Bloco de Comando Java: referência %s distância %d frontend %d backend %d comando %m.Command");
        PT_SPECS.put("CommandBlockXML",               "Bloco de Comando XML: referência %s distância %d frontend %d backend %d comando %m.Command nome xml %s.inputOnly");

        // Control / flow
        PT_SPECS.put("forLoopIncrease",              "para %m.varInt = %d; %b; %m.varInt++");
        PT_SPECS.put("repeatKnownNum",               "repetir %d: %s.inputOnly ++");
        PT_SPECS.put("RepeatKnownNumDescending",     "repetir %d: %s.inputOnly --");
        PT_SPECS.put("whileLoop",                    "enquanto %b");
        PT_SPECS.put("tryCatch",                     "tentar");
        PT_SPECS.put("switchStr",                    "switch %s");
        PT_SPECS.put("switchNum",                    "switch %d");
        PT_SPECS.put("caseStr",                      "caso %s");
        PT_SPECS.put("caseNum",                      "caso %d");
        PT_SPECS.put("defaultSwitch",                "padrão");
        PT_SPECS.put("notifyDataSetChanged",         "AtualizarDados");
        PT_SPECS.put("finishAffinity",               "Finalizar Afinidade");

        // Return
        PT_SPECS.put("returnString",                 "retornar %s");
        PT_SPECS.put("returnNumber",                 "retornar %d");
        PT_SPECS.put("returnBoolean",                "retornar %b");
        PT_SPECS.put("returnTitle",                  "retornar Título %s");
        PT_SPECS.put("returnFragment",               "retornar Fragment %m.activity");
        PT_SPECS.put("returnMap",                    "retornar %m.varMap");
        PT_SPECS.put("returnListStr",                "retornar %m.listStr");
        PT_SPECS.put("returnListMap",                "retornar %m.listMap");
        PT_SPECS.put("returnView",                   "retornar %m.view");

        // Source directly
        PT_SPECS.put("addSourceDirectly",            "adicionar código diretamente %s.inputOnly");
        PT_SPECS.put("asdBoolean",                   "booleano %s.inputOnly");
        PT_SPECS.put("asdNumber",                    "número %s.inputOnly");
        PT_SPECS.put("asdString",                    "string %s.inputOnly");

        // Ternary
        PT_SPECS.put("ternaryString",                "%b ? %s : %s");
        PT_SPECS.put("ternaryNumber",                "%b ? %d : %d");

        // EditText
        PT_SPECS.put("EditTextdiableSuggestion",     "%m.edittext desativar sugestões");
        PT_SPECS.put("EditTextLines",                "%m.edittext definir linhas %d");
        PT_SPECS.put("EditTextSingleLine",           "%m.edittext linhaÚnica? %b");
        PT_SPECS.put("EditTextShowError",            "%m.edittext mostrar erro %s");
        PT_SPECS.put("EditTextSelectAll",            "%m.edittext selecionar todo texto");
        PT_SPECS.put("EditTextSetSelection",         "%m.edittext definir seleção início %d fim %d");
        PT_SPECS.put("EditTextSetMaxLines",          "%m.edittext definir máximo de linhas %d");
        PT_SPECS.put("EdittextGetselectionStart",    "%m.edittext obter início da seleção");
        PT_SPECS.put("EdittextGetselectionEnd",      "%m.edittext obter fim da seleção");

        // ListView
        PT_SPECS.put("refreshingList",               "%m.listview atualizar views");
        PT_SPECS.put("ListViewAddHeader",            "%m.listview adicionar Header view %m.view dados %s selecionável? %b");
        PT_SPECS.put("ListViewAddFooter",            "%m.listview adicionar Footer view %m.view dados %s selecionável? %b");
        PT_SPECS.put("listViewRemoveHeader",         "%m.listview remover Header %m.view");
        PT_SPECS.put("listViewRemoveFooter",         "%m.listview remover Footer %m.view");
        PT_SPECS.put("listViewSetSelection",         "%m.listview definir seleção %d");
        PT_SPECS.put("getLastVisiblePosition",       "%m.listview obter última posição visível");
        PT_SPECS.put("listSetSelector",              "%m.listview definirSeletor %m.color");
        PT_SPECS.put("listSetTranscriptMode",        "%m.listview definirModoTranscrição %m.transcriptmode");
        PT_SPECS.put("listSetStackFromBottom",       "%m.listview empilharDaBaixo %b");

        // RecyclerView
        PT_SPECS.put("setRecyclerViewLayoutParams",      "%m.view definir params RecyclerView largura %m.LayoutParam altura %m.LayoutParam");
        PT_SPECS.put("recyclerSetCustomViewData",        "%m.recyclerview definirDadosRecycler %m.listMap");
        PT_SPECS.put("recyclerSetLayoutManager",         "%m.recyclerview definirLayoutManager");
        PT_SPECS.put("recyclerSetLayoutManagerHorizontal","%m.recyclerview definir LayoutManager Horizontal");
        PT_SPECS.put("recyclerSetHasFixedSize",          "%m.recyclerview definirTamanhoFixo %b");
        PT_SPECS.put("recyclerSmoothScrollToPosition",   "%m.recyclerview rolarSuavementePara %d");
        PT_SPECS.put("recyclerScrollToPositionWithOffset","%m.recyclerview rolarParaPosição %d deslocamento %d ");

        // GridView
        PT_SPECS.put("gridSetCustomViewData",        "%m.gridview definirDadosGrid %m.listMap");
        PT_SPECS.put("gridSetNumColumns",            "%m.gridview definirNúmeroColunas %d");
        PT_SPECS.put("gridSetColumnWidth",           "%m.gridview definirLarguraColuna %d");
        PT_SPECS.put("gridSetVerticalSpacing",       "%m.gridview definirEspaçamentoVertical %d");
        PT_SPECS.put("gridSetHorizontalSpacing",     "%m.gridview definirEspaçamentoHorizontal %d");
        PT_SPECS.put("gridSetStretchMode",           "%m.gridview definirModoEstiramento %m.gridstretchmode");

        // ViewPager
        PT_SPECS.put("pagerSetCustomViewData",       "%m.viewpager definirDadosPager %m.listMap");
        PT_SPECS.put("pagerSetFragmentAdapter",      "%m.viewpager definirAdaptadorFragment %m.fragmentAdapter QtdAbas %d");
        PT_SPECS.put("pagerGetOffscreenPageLimit",   "%m.viewpager obterLimitePaginaFora");
        PT_SPECS.put("pagerSetOffscreenPageLimit",   "%m.viewpager definirLimitePaginaFora %d");
        PT_SPECS.put("pagerGetCurrentItem",          "%m.viewpager obterItemAtual");
        PT_SPECS.put("pagerSetCurrentItem",          "%m.viewpager definirItemAtual %d");
        PT_SPECS.put("ViewPagerNotifyOnDtatChange",  "%m.viewpager notificarMudançaDados");

        // TabLayout
        PT_SPECS.put("addTab",                           "%m.tablayout adicionarAbaTítulo %s");
        PT_SPECS.put("setupWithViewPager",               "%m.tablayout configurarComViewPager %m.viewpager");
        PT_SPECS.put("setInlineLabel",                   "%m.tablayout definirLabelInterno %b");
        PT_SPECS.put("setTabTextColors",                 "%m.tablayout definirCoresTextoAba Normal %m.color Selecionado %m.color");
        PT_SPECS.put("setTabRippleColor",                "%m.tablayout definirCorRippleAba %m.color");
        PT_SPECS.put("setSelectedTabIndicatorColor",     "%m.tablayout definirCorIndicadorAbaSelecionada %m.color");
        PT_SPECS.put("setSelectedTabIndicatorHeight",    "%m.tablayout definirAlturaIndicadorAbaSelecionada %d");

        // SwipeRefreshLayout
        PT_SPECS.put("setRefreshing",                "%m.swiperefreshlayout definirAtualizando %b");

        // VideoView
        PT_SPECS.put("videoviewSetVideoUri",         "%m.videoview definirUriVideo %s");
        PT_SPECS.put("videoviewStart",               "%m.videoview iniciar");
        PT_SPECS.put("videoviewPause",               "%m.videoview pausar");
        PT_SPECS.put("videoviewStop",                "%m.videoview pararReproducao");
        PT_SPECS.put("videoviewIsPlaying",           "%m.videoview estáReproduzindo");
        PT_SPECS.put("videoviewCanPause",            "%m.videoview podePausar");
        PT_SPECS.put("videoviewCanSeekForward",      "%m.videoview podeAvançar");
        PT_SPECS.put("videoviewCanSeekBackward",     "%m.videoview podeRetroceder");
        PT_SPECS.put("videoviewGetDuration",         "%m.videoview obterDuração");
        PT_SPECS.put("videoviewGetCurrentPosition",  "%m.videoview obterPosicaoAtual");

        // ImageView
        PT_SPECS.put("setImageIdentifier",           "%m.imageview definir imagem por nome %s");
        PT_SPECS.put("setImageCustomRes",            "%m.imageview definirImagem %m.image");

        // View general
        PT_SPECS.put("getHeight",                    "%m.view obterAltura");
        PT_SPECS.put("getWidth",                     "%m.view obterLargura");
        PT_SPECS.put("removeView",                   "%m.view removerView %m.view");
        PT_SPECS.put("removeViews",                  "%m.view removerTodasViews");
        PT_SPECS.put("addView",                      "%m.view adicionarView %m.view");
        PT_SPECS.put("addViews",                     "%m.view adicionarView %m.view índice %d");
        PT_SPECS.put("setGravity",                   "%m.view definirGravidade %m.gravity_v %m.gravity_h");
        PT_SPECS.put("setElevation",                 "%m.view definirElevação %d");
        PT_SPECS.put("setColorFilterView",           "%m.view definirFiltroCorFundo %m.color com %m.porterduff");
        PT_SPECS.put("setCornerRadiusView",          "%m.view definirRaioCanto %d cor %m.color");
        PT_SPECS.put("setGradientBackground",        "%m.view definirFundoGradiente %m.color e %m.color");
        PT_SPECS.put("setStrokeView",                "%m.view definirBorda %d corBorda %m.color corFundo %m.color");
        PT_SPECS.put("setRadiusAndStrokeView",       "%m.view definirRaioCanto %d borda %d corBorda %m.color corFundo %m.color");
        PT_SPECS.put("showSnackbar",                 "%m.view exibirSnackbar texto %s textoBotão %s aoClicar");
        PT_SPECS.put("setBgDrawable",                "%m.view definirFundoDrawable %m.drawable");

        // TextView
        PT_SPECS.put("setTextSize",                  "%m.textview definirTamanhoTexto %d");

        // CardView
        PT_SPECS.put("setCardBackgroundColor",       "%m.cardview definirCorFundoCard %m.color");
        PT_SPECS.put("setCardRadius",                "%m.cardview definirRaioCanto %d");
        PT_SPECS.put("setCardElevation",             "%m.cardview definirElevacaoCard %d");
        PT_SPECS.put("setPreventCornerOverlap",      "%m.cardview prevenirSobreposicaoCanto %b");
        PT_SPECS.put("setUseCompatPadding",          "%m.cardview usarCompatPadding %b");

        // FAB
        PT_SPECS.put("fabIcon",                      "FAB definir ícone %m.resource");
        PT_SPECS.put("fabSize",                      "FAB definirTamanho %m.fabsize");
        PT_SPECS.put("fabVisibility",                "FAB definirVisibilidade %m.fabvisible");

        // BottomNavigation
        PT_SPECS.put("bottomMenuAddItem",            "%m.bottomnavigation adicionar item id %d título %s ícone %m.resource");

        // CodeView
        PT_SPECS.put("codeviewSetCode",              "%m.codeview definirCódigo %s");
        PT_SPECS.put("codeviewSetTheme",             "%m.codeview definirTema %m.cv_theme");
        PT_SPECS.put("codeviewSetLanguage",          "%m.codeview definirLinguagem %m.cv_language");
        PT_SPECS.put("codeviewApply",                "%m.codeview aplicar");

        // Lottie
        PT_SPECS.put("lottieSetAnimationFromAsset",  "%m.lottie definirAnimacaoDeAsset %s");
        PT_SPECS.put("lottieSetAnimationFromJson",   "%m.lottie definirAnimacaoDeJson %s");
        PT_SPECS.put("lottieSetAnimationFromUrl",    "%m.lottie definirAnimacaoDeUrl %s");
        PT_SPECS.put("lottieSetRepeatCount",         "%m.lottie definirContadorRepetição %d");
        PT_SPECS.put("lottieSetSpeed",               "%m.lottie definirVelocidade %d");

        // Spinner
        PT_SPECS.put("spnSetCustomViewData",         "%m.spinner definirDadosSpinner %m.listMap");

        // AutoComplete
        PT_SPECS.put("autoComSetData",               "%m.actv definirDadosLista %m.listStr");
        PT_SPECS.put("setThreshold",                 "%m.mactv definirLimiar %d");
        PT_SPECS.put("setTokenizer",                 "%m.mactv TokenizadorVírgula");
        PT_SPECS.put("multiAutoComSetData",          "%m.mactv definirDadosLista %m.listStr");

        // RatingBar
        PT_SPECS.put("getRating",                    "%m.ratingbar obterAvaliação");
        PT_SPECS.put("setRating",                    "%m.ratingbar definirAvaliação%d");
        PT_SPECS.put("setNumStars",                  "%m.ratingbar definirNúmeroEstrelas %d");
        PT_SPECS.put("setStepSize",                  "%m.ratingbar definirTamanhoPassp %d");

        // TimePicker
        PT_SPECS.put("timepickerSetIs24Hour",        "%m.timepicker definirFormato24h %b");
        PT_SPECS.put("timepickerSetCurrentHour",     "%m.timepicker definirHoraAtual %d");
        PT_SPECS.put("timepickerSetCurrentMinute",   "%m.timepicker definirMinutoAtual%d");
        PT_SPECS.put("timepickerSetHour",            "%m.timepicker definirHora %d");
        PT_SPECS.put("timepickerSetMinute",          "%m.timepicker definirMinuto%d");

        // BadgeView
        PT_SPECS.put("getBadgeCount",                "%m.badgeview obterContadorBadge");
        PT_SPECS.put("setBadgeNumber",               "%m.badgeview definirNúmeroBadge %d");
        PT_SPECS.put("setBadgeString",               "%m.badgeview definirTextoBadge %s");
        PT_SPECS.put("setBadgeBackground",           "%m.badgeview definirFundoBadge %m.color");
        PT_SPECS.put("setBadgeTextColor",            "%m.badgeview definirCorTextoBadge %m.color");
        PT_SPECS.put("setBadgeTextSize",             "%m.badgeview definirTamanhoTextoBadge %d");

        // BubbleLayout
        PT_SPECS.put("setBubbleColor",               "BubbleLayout %m.view definirCorBolha %m.color");
        PT_SPECS.put("setBubbleStrokeColor",         "BubbleLayout %m.view definirCorBorda %m.color");
        PT_SPECS.put("setBubbleStrokeWidth",         "BubbleLayout %m.view definirLarguraBorda %d");
        PT_SPECS.put("setBubbleCornerRadius",        "BubbleLayout %m.view definirRaioCanto %d");
        PT_SPECS.put("setBubbleArrowHeight",         "BubbleLayout %m.view definirAlturaSetinha %d");
        PT_SPECS.put("setBubbleArrowWidth",          "BubbleLayout %m.view definirLarguraSetinha %d");
        PT_SPECS.put("setBubbleArrowPosition",       "BubbleLayout %m.view definirPosicaoSetinha %d");

        // SideBar
        PT_SPECS.put("setCustomLetter",              "%m.sidebar definirLetraPersonalizada String[] %s.inputOnly");

        // PatternView
        PT_SPECS.put("patternToString",              "%m.patternview obterPadrão de %m.listStr para String ");
        PT_SPECS.put("patternToMD5",                 "%m.patternview obterPadrão de %m.listStr para MD5");
        PT_SPECS.put("patternToSha1",                "%m.patternview obterPadrão de %m.listStr para SHA1");
        PT_SPECS.put("patternSetDotCount",           "%m.patternview definirQtdPontos %d ");
        PT_SPECS.put("patternSetNormalStateColor",   "%m.patternview definirCorEstadoNormal %m.color");
        PT_SPECS.put("patternSetCorrectStateColor",  "%m.patternview definirCorEstadoCorreto %m.color");
        PT_SPECS.put("patternSetWrongStateColor",    "%m.patternview definirCorEstadoErrado %m.color");
        PT_SPECS.put("patternSetViewMode",           "%m.patternview definirModoVisualizacao %m.patternviewmode");
        PT_SPECS.put("patternLockClear",             "%m.patternview limparPadrão");

        // TextInputLayout
        PT_SPECS.put("tilSetBoxBgColor",             "%m.textinputlayout definirCorFundoCaixa %m.color");
        PT_SPECS.put("tilSetBoxStrokeColor",         "%m.textinputlayout definirCorBordaCaixa %m.color");
        PT_SPECS.put("tilSetBoxBgMode",              "%m.textinputlayout definirModoCaixa %m.til_box_mode");
        PT_SPECS.put("tilSetBoxCornerRadii",         "%m.textinputlayout definirRaiosCanto TL %d TR %d BL %d BR %d ");
        PT_SPECS.put("tilSetError",                  "%m.textinputlayout definirErro %s ");
        PT_SPECS.put("tilSetErrorEnabled",           "%m.textinputlayout ativarErro %b ");
        PT_SPECS.put("tilSetCounterEnabled",         "%m.textinputlayout ativarContador %b ");
        PT_SPECS.put("tilSetCounterMaxLength",       "%m.textinputlayout definirComprimentoMáxContador %d ");
        PT_SPECS.put("tilGetCounterMaxLength",       "%m.textinputlayout obterComprimentoMáxContador");

        // YouTube
        PT_SPECS.put("YTPVLifecycle",                "%m.youtubeview obterCicloDeVida");
        PT_SPECS.put("YTPVSetListener",              "%m.youtubeview adicionarOuvinte VideoID %s");

        // ProgressDialog
        PT_SPECS.put("progressdialogCreate",             "%m.progressdialog Criar em %m.activity");
        PT_SPECS.put("progressdialogSetCanceledOutside", "%m.progressdialog setCancelableAoTocarFora %b");
        PT_SPECS.put("progressdialogSetTitle",           "%m.progressdialog definirTítulo %s");
        PT_SPECS.put("progressdialogSetMessage",         "%m.progressdialog definirMensagem %s");
        PT_SPECS.put("progressdialogSetMax",             "%m.progressdialog definirMáximo %d");
        PT_SPECS.put("progressdialogSetProgress",        "%m.progressdialog definirProgresso %d");
        PT_SPECS.put("progressdialogSetCancelable",      "%m.progressdialog setCancelável %b");
        PT_SPECS.put("progressdialogSetCanceled",        "%m.progressdialog setCanceladoAoTocarFora %b");
        PT_SPECS.put("progressdialogSetStyle",           "%m.progressdialog definirEstiloProgresso %m.styleprogress");
        PT_SPECS.put("progressdialogDismiss",            "%m.progressdialog fechar");
        PT_SPECS.put("progressdialogShow",               "%m.progressdialog exibir");

        // DatePicker / TimePicker dialogs
        PT_SPECS.put("datePickerDialogShow",         "DatePickerDialog exibir");
        PT_SPECS.put("timePickerDialogShow",         "%m.timepickerdialog exibir");

        // AsyncTask
        PT_SPECS.put("AsyncTaskExecute",             "%m.asynctask executar mensagem %s");
        PT_SPECS.put("AsyncTaskPublishProgress",     "publicar progresso %d");

        // Service / Broadcast
        PT_SPECS.put("startService",                 "iniciarServiço %m.activity");
        PT_SPECS.put("stopService",                  "pararServiço %m.activity");
        PT_SPECS.put("sendBroadcast",                "enviarBroadcast %s");
        PT_SPECS.put("startActivityWithChooser",     "IniciarActivity %m.intent com Seletor %s");

        // Intent / Activity
        PT_SPECS.put("launchApp",                    "%m.intent definirPacoteApp %s");
        PT_SPECS.put("changeStatebarColour",         "%m.activity definir cor barra de status %m.color");
        PT_SPECS.put("Dialog SetIcon",               "%m.dialog definirÍcone %m.resource_bg");

        // Image Crop
        PT_SPECS.put("imageCrop",                    "CropImageView doCaminho %s CódigoRequisição %d");

        // Misc
        PT_SPECS.put("isConnected",                  "estáConectado");
        PT_SPECS.put("LightStatusBar",               "BarraDeStatusClara");
        PT_SPECS.put("hideKeyboard",                 "Ocultar teclado");
        PT_SPECS.put("showKeyboard",                 "Mostrar teclado");
        PT_SPECS.put("customImport",                 "importar %s.import");
        PT_SPECS.put("customImport2",                "importar %m.import");
        PT_SPECS.put("customToast",                  "Toast %s corTexto %m.color tamanhoTexto %d corFundo %m.color raioCantos %d gravidade %m.gravity_t");
        PT_SPECS.put("customToastWithIcon",          "Toast+Ícone %s corTexto %m.color tamanhoTexto %d corFundo %m.color raioCantos %d gravidade %m.gravity_t Ícone %m.resource");

        // String ops
        PT_SPECS.put("html",                         "html %s");
        PT_SPECS.put("reverse",                      "inverter %s");
        PT_SPECS.put("toHashCode",                   "paraHashCode %s");
        PT_SPECS.put("stringMatches",                "%s corresponde RegExp %s");
        PT_SPECS.put("stringReplaceFirst",           "%s substituir primeiro RegExp %s por %s");
        PT_SPECS.put("stringReplaceAll",             "%s substituir tudo RegExp %s por %s");
        PT_SPECS.put("stringSplitToList",            "dividir %s RegExp %s em %m.listStr");

        // Map ops
        PT_SPECS.put("mapContainValue",              "%m.varMap contém valor %s");
        PT_SPECS.put("sortListmap",                  "ordenar %m.listMap chave %s éNúmero %b éCrescente %b");
        PT_SPECS.put("deleteMapFromListmap",         "excluir %m.varMap de %m.listMap");

        // Collections
        PT_SPECS.put("reverseList",                  "inverter %m.list");
        PT_SPECS.put("shuffleList",                  "embaralhar %m.list");
        PT_SPECS.put("sortList",                     "ordenar %m.listStr");
        PT_SPECS.put("sortListnum",                  "ordenar %m.listInt");
        PT_SPECS.put("swapInList",                   "trocar %m.list posição %d com %d");
        PT_SPECS.put("getMapAtPosListmap",           "obter Mapa em %d de %m.listMap");
        PT_SPECS.put("setMapAtPosListmap",           "definir %m.varMap em %d de %m.listMap");
        PT_SPECS.put("setAtPosListstr",              "definir %s em %d de %m.listStr");
        PT_SPECS.put("setAtPosListnum",              "definir %d em %d de %m.listInt");
        PT_SPECS.put("GsonListTojsonString",         "%m.list para String JSON");
        PT_SPECS.put("GsonStringToListString",       "JSON %s para %m.listStr");
        PT_SPECS.put("GsonStringToListNumber",       "JSON %s para %m.listInt");

        // HashMap typed ops
        PT_SPECS.put("hashmapGetNumber",             "%m.varMap obter número chave %s");
        PT_SPECS.put("hashmapPutNumber",             "%m.varMap inserir chave %s valor int %d");
        PT_SPECS.put("hashmapPutNumber2",            "%m.varMap inserir chave %s valor double %d");
        PT_SPECS.put("hashmapGetBoolean",            "%m.varMap obter booleano chave %s");
        PT_SPECS.put("hashmapPutBoolean",            "%m.varMap inserir chave %s valor %b");
        PT_SPECS.put("hashmapGetMap",                "%m.varMap obter Mapa chave %s");
        PT_SPECS.put("hashmapPutMap",                "%m.varMap inserir chave %s valor %m.varMap");
        PT_SPECS.put("hashmapListstr",               "%m.varMap obter Lista String chave %s");
        PT_SPECS.put("hashmapPutListstr",            "%m.varMap inserir chave %s valor %m.listStr");
        PT_SPECS.put("hashmapGetListmap",            "%m.varMap obter Lista Mapa chave %s");
        PT_SPECS.put("hashmapPutListmap",            "%m.varMap inserir chave %s valor %m.listMap");

        // File / Assets
        PT_SPECS.put("getAssetFile",                 "%m.inputstream obterArquivoDeAsset caminho %s");
        PT_SPECS.put("renameFile",                   "renomear arquivo caminho %s para %s");
        PT_SPECS.put("copyAssetFile",                "%m.inputstream para String");

        // Menu
        PT_SPECS.put("menuInflater",                 "Menu obter menu do arquivo %m.menu");
        PT_SPECS.put("menuAddItem",                  "Menu adicionar id %d título %s");
        PT_SPECS.put("menuAddMenuItem",              "%m.menuitem adicionar id %d título %s ícone %m.resource exibirComoAção %m.menuaction");
        PT_SPECS.put("menuAddSubmenu",               "Menu adicionar %m.submenu título %s;");
        PT_SPECS.put("submenuAddItem",               "%m.submenu adicionar id %d título %s");

        // AdMob
        PT_SPECS.put("interstitialAdLoad",               "%m.interstitialad carregar em %m.activity");
        PT_SPECS.put("interstitialAdShow",               "%m.interstitialad exibir anúncio em %m.activity");
        PT_SPECS.put("interstitialAdIsLoaded",           "%m.interstitialad está carregado");
        PT_SPECS.put("interstitialAdRegisterFullScreenContentCallback",
                "%m.interstitialad registrar callbacks tela cheia (Este bloco não é mais necessário, remova-o)");
        PT_SPECS.put("rewardedAdRegisterFullScreenContentCallback",
                "%m.videoad registrar callbacks tela cheia (Este bloco não é mais necessário, remova-o)");

        // String Resource
        PT_SPECS.put("getResString",                 "obter String de %m.ResString");
    }

    /** Returns true when device/app locale is PT (any variant). */
    public static boolean isPtLocale() {
        return Locale.getDefault().getLanguage().startsWith("pt");
    }

    /**
     * Applies PT-BR spec translations to a block list in-place.
     * Only runs when locale is PT. Never touches "name", "code", or "type".
     */
    public static void applyIfPt(ArrayList<HashMap<String, Object>> blocks) {
        if (!isPtLocale()) return;
        for (HashMap<String, Object> block : blocks) {
            Object nameObj = block.get("name");
            if (!(nameObj instanceof String)) continue;
            String name = (String) nameObj;
            String ptSpec = PT_SPECS.get(name);
            if (ptSpec != null) {
                block.put("spec", ptSpec);
            }
        }
    }
}
