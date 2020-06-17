package nl.vu.cs.s2group.nappa.sample.app.yetanotherpokemonlist.pokemon;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class PokemonsViewModel extends ViewModel {
    private LiveData<PagedList<Pokemon>> pokemonLiveData;

    public PokemonsViewModel() {
        Executor executor = Executors.newFixedThreadPool(5);

        PokemonsDataFactory pokemonsDataFactory = new PokemonsDataFactory();

        PagedList.Config pagedListConfig =
                (new PagedList.Config.Builder())
                        .setEnablePlaceholders(false)
                        .setInitialLoadSizeHint(10)
                        .setPageSize(20).build();

        pokemonLiveData = (new LivePagedListBuilder<>(pokemonsDataFactory, pagedListConfig))
                .setFetchExecutor(executor)
                .build();
    }

    public LiveData<PagedList<Pokemon>> getPokemonLiveData() {
        return pokemonLiveData;
    }
}
