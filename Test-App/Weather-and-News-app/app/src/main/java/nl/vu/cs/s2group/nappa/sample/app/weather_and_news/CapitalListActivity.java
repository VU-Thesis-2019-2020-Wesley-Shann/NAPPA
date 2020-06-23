package nl.vu.cs.s2group.nappa.sample.app.weather_and_news;

import android.os.Bundle;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.Observable;
import nl.vu.cs.s2group.nappa.sample.app.weather_and_news.R;
import nl.vu.cs.s2group.nappa.sample.app.weather_and_news.cardview.CapitalCardViewAdapter;
import nl.vu.cs.s2group.nappa.sample.app.weather_and_news.cardview.CapitalCardViewAdapterObservable;
import nl.vu.cs.s2group.nappa.sample.app.weather_and_news.data.Capital;
import nl.vu.cs.s2group.nappa.PrefetchingLib;

public class CapitalListActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getLifecycle().addObserver(new NAPPALifecycleObserver(this));

        setContentView(R.layout.activity_capital_list);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });


        Type type = new TypeToken<ArrayList<Capital>>() {}.getType();

        List<Capital> capitalList = new Gson().fromJson(
                new InputStreamReader(getResources().openRawResource(R.raw.capitals)),
                type
        );

        RecyclerView recyclerView = findViewById(R.id.recycler_capitals);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 1));

        CapitalCardViewAdapter adapter = new CapitalCardViewAdapter();
        CapitalCardViewAdapterObservable adapter2 = new CapitalCardViewAdapterObservable();
        Observable<Capital> capitalObservable = Observable.fromIterable(capitalList);

        recyclerView.setAdapter(adapter2);

        adapter2.capitalList = capitalObservable;

        TextInputEditText textInputEditText = findViewById(R.id.text_capital_filter);
        textInputEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });


    }
}
