package com.techmagic.wordpress.audioplayer;

import android.content.Context;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Collections;
import java.util.List;

/**
 * Created by Keerthi Prasad on 9/1/2017.
 */

public class RecyclerView_Adapter extends RecyclerView.Adapter<RecyclerView_Adapter.ViewHolder> {

    private List<Audio> list = Collections.emptyList();
    private Context context;

    public RecyclerView_Adapter(List<Audio> list, Context context) {
        this.list = list;
        this.context = context;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_layout,parent,false);
        ViewHolder holder = new ViewHolder(v);
        return holder;
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        holder.title.setText(list.get(position).getTitle());
        if (list.get(position).getAlbum_art() != null) {
            holder.play_pause.setImageBitmap(list.get(position).getAlbum_art());
        }else {
            holder.play_pause.setImageDrawable(ContextCompat.getDrawable(context,R.drawable.images));
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    @Override
    public void onAttachedToRecyclerView(RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
    }

    class ViewHolder extends RecyclerView.ViewHolder{

        private TextView title;
        private ImageView play_pause;

        public ViewHolder(View itemView) {
            super(itemView);
            title = (TextView) itemView.findViewById(R.id.title);
            play_pause = (ImageView) itemView.findViewById(R.id.play_pause);
        }
    }
}
