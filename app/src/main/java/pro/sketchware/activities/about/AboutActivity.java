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
                me.setUsername("Marcos Santos SK-SDBCodFlow");
                me.setDescription("DEV único nesta versão");
                me.setImg("LOCAL_DRAWABLE:" + R.drawable.sdbcodflow_avatar_v2);
                me.setCoreTeam(true);
                me.setActive(true);
                team.add(0, me);
            }

            java.util.ArrayList<AboutResponseModel.ChangeLogs> changelog = aboutResponseModel.getChangelog();
            if (changelog != null) {
                AboutResponseModel.ChangeLogs newVersion = new AboutResponseModel.ChangeLogs();
                newVersion.setTitle("SdbCodFlow 8.7.7");
                newVersion.setDescription("🤖 The SDBCodFlow Agent is here with full power for Sketchware Pro!\n\nAI now reads, creates, and edits your project in real time — directly in chat. Inject Java code, create and edit MoreBlocks, design interfaces with XML, add drawables and vector icons without leaving the app.\n\n🔹 What's new in this version:\n- AI Chat integrated in all contexts: Logic Editor, XML Editor, and main project screen.\n- Create and edit MoreBlocks via chat (add_moreblock / update_moreblock / delete_moreblock).\n- Edit and delete Drawables (add_drawable / delete_drawable).\n- Create, edit, and remove Widgets in the layout (add_widget / update_widget / remove_widget).\n- Inject global code in any screen and event (inject_code).\n- Create blocks in the custom block palette (add_custom_block).\n- Conversation history per project saved automatically.\n- Image support: send a screenshot and AI applies the design.\n- Bilingual PT 🇧🇷 / EN 🇺🇸 interface with language toggle.\n- Promotional banner supporting the developer.\n\n🔹 More info and updates:\nhttps://sketch-dev-brasil.web.app/sdbcodflow\n\n🔹 Telegram community:\nhttps://t.me/sketchdevbrasil\n\n🔹 YouTube:\nhttps://youtube.com/@sketchdevbrasil");
                newVersion.setDescriptionPt("🤖 O Agente SDBCodFlow chegou com tudo para o Sketchware Pro!\n\nA IA agora lê, cria e edita seu projeto em tempo real — diretamente no chat. Injete código Java, crie e edite MoreBlocks, desenhe interfaces com XML, adicione drawables e ícones vetoriais sem sair do app.\n\n🔹 Novidades desta versão:\n- Chat IA integrado em todos os contextos: Logic Editor, XML Editor e tela principal do projeto.\n- Criação e edição de MoreBlocks via chat (add_moreblock / update_moreblock / delete_moreblock).\n- Edição e exclusão de Drawables (add_drawable / delete_drawable).\n- Criação, edição e remoção de Widgets no layout (add_widget / update_widget / remove_widget).\n- Injeção de código global em qualquer tela e evento (inject_code).\n- Criação de blocos na Paleta de blocos customizada (add_custom_block).\n- Histórico de conversas por projeto salvo automaticamente.\n- Suporte a imagens: envie um screenshot e a IA aplica o design.\n- Interface bilíngue PT 🇧🇷 / EN 🇺🇸 com botão de troca.\n- Banner publicitário de apoio ao desenvolvedor.\n\n🔹 Mais informações e atualizações:\nhttps://sketch-dev-brasil.web.app/sdbcodflow\n\n🔹 Comunidade no Telegram:\nhttps://t.me/sketchdevbrasil\n\n🔹 YouTube:\nhttps://youtube.com/@sketchdevbrasil");
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
