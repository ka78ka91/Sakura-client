package com.naruka.client.setting;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Any number of named entries out of a fixed set, e.g. which entity types a visual module should highlight.
 *
 * <p>Equivalent to LiquidBounce's {@code MultiChoiceListValue} (GPL-3.0).</p>
 *
 * @param <E> the choice enum
 */
public class MultiChoiceSetting<E extends Enum<E>> extends Setting<Set<E>> {

	private final List<E> choices;

	public MultiChoiceSetting(String name, String description, java.util.Collection<E> defaultSelection,
							  Class<E> choiceClass) {
		super(name, description, new LinkedHashSet<>(defaultSelection));
		this.choices = List.of(choiceClass.getEnumConstants());
	}

	@Override
	public SettingType type() {
		return SettingType.MULTI_CHOICE;
	}

	public List<E> getChoices() {
		return this.choices;
	}

	public boolean contains(E choice) {
		return get().contains(choice);
	}

	/** Adds or removes {@code choice}; removing the last entry is allowed so a module can be muted. */
	public void toggle(E choice) {
		Set<E> copy = new LinkedHashSet<>(get());

		if (!copy.remove(choice)) {
			copy.add(choice);
		}

		set(copy);
	}

	public String labelOf(E choice) {
		return choice instanceof Tagged tagged ? tagged.getTag() : choice.name();
	}

	@Override
	public void set(Set<E> newValue) {
		if (newValue == null) {
			return;
		}

		// Keep declaration order so the config file and the label stay stable.
		Set<E> ordered = new LinkedHashSet<>();

		for (E choice : this.choices) {
			if (newValue.contains(choice)) {
				ordered.add(choice);
			}
		}

		super.set(ordered);
	}

	@Override
	public Object toConfig() {
		List<String> names = new ArrayList<>();

		for (E choice : get()) {
			names.add(choice.name());
		}

		return names;
	}

	@Override
	public void fromConfig(Object raw) {
		if (!(raw instanceof List<?> list)) {
			return;
		}

		Set<E> restored = new LinkedHashSet<>();

		for (Object entry : list) {
			if (!(entry instanceof String name)) {
				continue;
			}

			for (E choice : this.choices) {
				if (choice.name().equalsIgnoreCase(name)) {
					restored.add(choice);
				}
			}
		}

		set(restored);
	}

	@Override
	public String displayValue() {
		if (get().isEmpty()) {
			return "none";
		}

		StringBuilder builder = new StringBuilder();

		for (E choice : get()) {
			if (builder.length() > 0) {
				builder.append(", ");
			}

			builder.append(labelOf(choice));
		}

		return get().size() + "/" + this.choices.size() + " (" + builder + ")";
	}
}
