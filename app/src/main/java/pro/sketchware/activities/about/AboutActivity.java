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
                newVersion.setTitle("Sketchware Pro SdbCodFlow v8.7.7");
                newVersion.setDescription("Aqui chegou o SDBCodFlow AI Agent,para Sketchware Pro SdbCodFlow v8.7.7 a Inteligência Artificial revolucionária embutida diretamente no coração do Sketchware Pro! 🚀\n\nNossas melhorias exclusivas permitem que a IA leia o seu Código Java ativo, edite os Arquivos XML da tela (cores, textos, widgets) e crie novos Blocos Diretos na sua Lógica (em Eventos OnCreate, OnClick, etc).\n\nVocê não precisa mais desenvolver no escuro: desenhe interfaces incríveis, adicione backgrounds e crie tudo que o sketchware permite na interface de design ui ux sem precisar arrastar um elemento e gere componentes visuais e lógicas complexas apenas conversando com o chat de ia em tempo real, com o Agente dentro do seu projeto!\n\n🔹 Novidades desta atualização:\n- Ícone padronizado estilo 'star'.\n- Integração avançada no Compile Log (O Agente analisa seus erros de compilação!).\n- Disponível diretamente também no Code / XML Editor.\n- Capacidade incrível de forjar novos Events (Activity: Import)  gerar estruturas de MoreBlocks, criar paletas de blocos para logicas globais, criar e injetar o boco vere de codigo direto ja com codigo imbutido nos eventos de logicas, totalmente via Chat!\n\n🔹para mais informaões fique ligado na page da IA em Site Oficial: https://sketch-dev-brasil.web.app/SdbCodFlow\n🔹 Comunidade Telegram: https://t.me/sketchdevbrasil");
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
