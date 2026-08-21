package pro.sketchware.activities.about;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.gson.Gson;

import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.activities.about.fragments.ChangeLogFragment;
import pro.sketchware.activities.about.fragments.TeamFragment;
import pro.sketchware.activities.about.models.AboutAppViewModel;
import pro.sketchware.activities.about.models.AboutResponseModel;
import pro.sketchware.databinding.ActivityAboutAppBinding;
import pro.sketchware.utility.Network;

public class AboutActivity extends BaseAppCompatActivity {

    private final Network network = new Network();
    public AboutAppViewModel aboutAppData;
    private ActivityAboutAppBinding binding;
    private SharedPreferences sharedPref;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);

        binding = ActivityAboutAppBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        aboutAppData = new ViewModelProvider(this).get(AboutAppViewModel.class);
        sharedPref = getSharedPreferences("AppData", Activity.MODE_PRIVATE);

        initViews();
        initData();
    }

    private void initViews() {
        binding.toolbar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));
        AboutAdapter adapter = new AboutAdapter(this);
        binding.viewPager.setOffscreenPageLimit(2);
        binding.viewPager.setAdapter(adapter);

        String[] tabTitles = new String[]{
                Helper.getResString(R.string.about_team_title),
                Helper.getResString(R.string.about_changelog_title)
        };

        new TabLayoutMediator(binding.tabLayout, binding.viewPager, (tab, position) -> tab.setText(tabTitles[position])).attach();

        String toSelect = getIntent().getStringExtra("select");
        if (toSelect != null) {
            if ("changelog".equals(toSelect)) {
                binding.viewPager.setCurrentItem(1);
            }
        }
    }

    private void initData() {
        network.get(Helper.getResString(R.string.link_about_team), response -> {
            if (response != null) {
                sharedPref.edit().putString("aboutData", response).apply();
            } else {
                response = sharedPref.getString("aboutData", null);
            }
            if (response == null) return;

            Gson gson = new Gson();
            AboutResponseModel aboutResponseModel = gson.fromJson(response, AboutResponseModel.class);

            java.util.ArrayList<AboutResponseModel.TeamMember> team = aboutResponseModel.getTeam();
            if (team != null) {
                for (AboutResponseModel.TeamMember m : team) {
                    m.setActive(false);
                }
                AboutResponseModel.TeamMember me = new AboutResponseModel.TeamMember();
                me.setUsername("Marcos Santos - Sketchware-GC-AI");
                me.setDescription("DEV único nesta versão");
                me.setImg("LOCAL_DRAWABLE:" + R.drawable.sdbcodflow_avatar_v2);
                me.setCoreTeam(true);
                me.setActive(true);
                team.add(0, me);
            }

            java.util.ArrayList<AboutResponseModel.ChangeLogs> changelog = aboutResponseModel.getChangelog();
            if (changelog != null) {
                AboutResponseModel.ChangeLogs newVersion = new AboutResponseModel.ChangeLogs();
                newVersion.setTitle("Sketchware-GC-AI 9.8.8");
                newVersion.setDescription("Sketchware-GC-AI 9.8.8 is a major evolution of the Genesis Code AI experience inside Sketchware. The chat is now the central workspace for understanding, creating, editing, validating, and repairing complete applications.\n\nINTERNAL ENGINEERING AND RELIABILITY\n- Reworked internal mutation, validation, direct-file, compile-repair, and project-context engines.\n- Atomic operations with preflight validation, snapshots, rollback, detailed reports, and safer project refresh.\n- Smarter handling of IDs, variables, lists, components, imports, events, Activities, layouts, and Java files.\n- Up to six automatic repair attempts, with protection against repeated invalid responses.\n- MoreBlock names, parameters, calls, and visual events are normalized and synchronized with Sketchware.\n\nCENTRALIZED GC-AI CHAT\n- One consistent agent across the main project screen, Design, Logic, XML/code editors, MoreBlocks, and compilation errors.\n- Separate conversations and project history, individual deletion, task notifications, interruption control, and animated work status.\n- Approve, Agent, and Plan modes provide a clearer and more predictable workflow.\n\nCREATION, EDITING, AND DESIGN\n- Create and edit native Activities, XML layouts, widgets, IDs, drawables, icons, Material 3 interfaces, custom views, events, variables, lists, and components.\n- Real-time interface refresh keeps internal project data and the visual editor aligned.\n- Refined chat colors, spacing, cards, controls, responsive layout, and consistent GC-AI branding.\n\nAI PROVIDERS AND TOOLS\n- Updated model support for Gemini, OpenRouter, OpenAI, Claude, NVIDIA, and DeepSeek, including DeepSeek V4 Flash and V4 Pro.\n- Image-assisted design, code preview, save, undo, project inspection, and contextual compilation repair remain available directly in chat.\n\nOfficial page:\nhttps://sketch-dev-brasil.web.app/sk-gc-ai");
                newVersion.setDescriptionPt("O Sketchware-GC-AI 9.8.8 representa uma grande evolução da experiência Genesis Code AI dentro do Sketchware. O chat agora é o centro de trabalho para compreender, criar, editar, validar e reparar aplicativos completos.\n\nENGENHARIA INTERNA E CONFIABILIDADE\n- Reestruturação dos motores internos de mutação, validação, arquivos diretos, correção de compilação e leitura do contexto do projeto.\n- Operações atômicas com validação prévia, snapshots, rollback, relatórios detalhados e atualização segura do projeto.\n- Tratamento mais inteligente de IDs, variáveis, listas, componentes, imports, eventos, Activities, layouts e arquivos Java.\n- Até seis tentativas de correção automática, com proteção contra respostas inválidas repetidas.\n- Nomes, parâmetros, chamadas e eventos visuais de MoreBlocks são normalizados e sincronizados com o Sketchware.\n\nCHAT GC-AI CENTRALIZADO\n- Um único agente consistente na tela principal do projeto, Design, Lógica, editores XML/código, MoreBlocks e erros de compilação.\n- Conversas e históricos separados por projeto, exclusão individual, notificações de tarefas, controle de interrupção e status animado de trabalho.\n- Modos Aprovar, Agente e Plano deixam o fluxo mais claro, previsível e eficaz.\n\nCRIAÇÃO, EDIÇÃO E DESIGN\n- Criação e edição de Activities nativas, layouts XML, widgets, IDs, drawables, ícones, interfaces Material 3, custom views, eventos, variáveis, listas e componentes.\n- Atualização visual em tempo real mantém os dados internos do projeto alinhados com o editor visual.\n- Cores, espaçamentos, cartões, controles, responsividade e identidade GC-AI foram refinados em toda a experiência do chat.\n\nAGENTES, MODELOS E FERRAMENTAS\n- Catálogos atualizados para Gemini, OpenRouter, OpenAI, Claude, NVIDIA e DeepSeek, incluindo DeepSeek V4 Flash e V4 Pro.\n- Design assistido por imagem, visualização de código, salvar, desfazer, inspeção de projeto e correção contextual de compilação continuam disponíveis diretamente no chat.\n\nPágina oficial:\nhttps://sketch-dev-brasil.web.app/sk-gc-ai");
                newVersion.setReleaseDate(System.currentTimeMillis());
                newVersion.setBeta(false);
                newVersion.setTitled(true);
                changelog.add(0, newVersion);
            }

            aboutAppData.setDiscordInviteLink(aboutResponseModel.getDiscordInviteLink());
            aboutAppData.setTeamMembers(team);
            aboutAppData.setChangelog(changelog);
        });
    }

    // ----------------- classes ----------------- //

    public static class AboutAdapter extends FragmentStateAdapter {
        public AboutAdapter(AppCompatActivity activity) {
            super(activity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return switch (position) {
                case 1 -> new ChangeLogFragment();
                default -> new TeamFragment();
            };
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }
}
