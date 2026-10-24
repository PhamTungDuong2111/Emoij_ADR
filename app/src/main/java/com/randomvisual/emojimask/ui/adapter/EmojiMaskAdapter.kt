package com.randomvisual.emojimask.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.randomvisual.emojimask.R
import com.randomvisual.emojimask.databinding.ItemEmojiMaskBinding
import com.randomvisual.emojimask.model.EmojiMaskType

class EmojiMaskAdapter(
    private val masks: List<EmojiMaskType>,
    private val onMaskSelected: (EmojiMaskType) -> Unit
) : RecyclerView.Adapter<EmojiMaskAdapter.MaskViewHolder>() {

    private var selectedIndex = 0

    fun getSelectedMask(): EmojiMaskType = masks[selectedIndex]

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MaskViewHolder {
        val binding = ItemEmojiMaskBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MaskViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MaskViewHolder, position: Int) {
        holder.bind(masks[position], position == selectedIndex)
    }

    override fun getItemCount(): Int = masks.size

    inner class MaskViewHolder(private val binding: ItemEmojiMaskBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(mask: EmojiMaskType, isSelected: Boolean) {
            binding.tvMaskEmoji.text = mask.emoji
            binding.tvMaskTitle.setText(mask.titleRes)

            if (isSelected) {
                binding.maskItemContainer.setBackgroundResource(R.drawable.bg_mask_item_selected)
                binding.tvMaskTitle.setTextColor(binding.root.context.getColor(R.color.color_primary))
                binding.root.scaleX = 1.08f
                binding.root.scaleY = 1.08f
            } else {
                binding.maskItemContainer.setBackgroundResource(R.drawable.bg_mask_item_unselected)
                binding.tvMaskTitle.setTextColor(binding.root.context.getColor(R.color.text_secondary))
                binding.root.scaleX = 1.0f
                binding.root.scaleY = 1.0f
            }

            binding.root.setOnClickListener {
                val prev = selectedIndex
                selectedIndex = bindingAdapterPosition
                notifyItemChanged(prev)
                notifyItemChanged(selectedIndex)
                onMaskSelected(mask)
            }
        }
    }
}
