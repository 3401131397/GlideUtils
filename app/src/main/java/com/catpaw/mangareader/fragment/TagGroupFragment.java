package com.catpaw.mangareader.fragment;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.catpaw.mangareader.R;
import com.catpaw.mangareader.api.JMApiClient;
import com.catpaw.mangareader.model.TagItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TagGroupFragment extends Fragment {

    private LinearLayout containerLayout;
    private Map<String, List<TagItem>> tagGroups;

    public TagGroupFragment() {
        super(R.layout.fragment_tag_group);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tag_group, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        containerLayout = view.findViewById(R.id.tag_container);
        tagGroups = new LinkedHashMap<>();

        loadTags();
    }

    private void loadTags() {
        JMApiClient.getInstance().getTagGroups(new JMApiClient.ApiCallback<List<TagItem>>() {
            @Override
            public void onSuccess(List<TagItem> tags) {
                groupTagsByGroup(tags);
                displayTagGroups();
            }

            @Override
            public void onFailure(String error) {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Failed to load tags: " + error, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void groupTagsByGroup(List<TagItem> tags) {
        tagGroups.clear();
        for (TagItem tag : tags) {
            String group = tag.getTagGroup();
            if (group == null || group.isEmpty()) {
                group = "Other";
            }
            if (!tagGroups.containsKey(group)) {
                tagGroups.put(group, new ArrayList<>());
            }
            tagGroups.get(group).add(tag);
        }
    }

    private void displayTagGroups() {
        containerLayout.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getContext());

        for (Map.Entry<String, List<TagItem>> entry : tagGroups.entrySet()) {
            View groupView = inflater.inflate(R.layout.item_tag_group, containerLayout, false);

            TextView groupTitle = groupView.findViewById(R.id.tv_group_title);
            com.google.android.flexbox.FlexboxLayout chipsContainer = groupView.findViewById(R.id.flexbox_tags);

            groupTitle.setText(entry.getKey());

            for (TagItem tag : entry.getValue()) {
                TextView chipView = (TextView) inflater.inflate(R.layout.item_tag_chip, chipsContainer, false);
                chipView.setText(tag.getTagName());
                chipView.setOnClickListener(v -> onTagClicked(tag));
                chipsContainer.addView(chipView);
            }

            containerLayout.addView(groupView);
        }
    }

    private void onTagClicked(TagItem tag) {
        if (getActivity() instanceof OnTagSearchListener) {
            ((OnTagSearchListener) getActivity()).onTagSearch(tag.getTagName());
        }
    }

    public interface OnTagSearchListener {
        void onTagSearch(String tagName);
    }
}
