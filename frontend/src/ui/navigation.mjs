// Pure coordinator: authorization invalidates even an outstanding leave/save dialog.
export function createNavigationCoordinator(ask, onState = () => {}) {
  const editors = new Map();
  const preferences = new Map();
  let identity = '', generation = 0, pending = false, saving = 0;
  const publish=()=>onState({pending,saving});
  return {
    register(id, editor) { editors.set(id, editor); return () => editors.delete(id); },
    get dirty() { return [...editors.values()].some(e => e.dirty()); },
    get pending() { return pending; },
    setIdentity(user) { const next = user ? `${user.id}:${user.role}` : ''; if (next !== identity) { this.clear(); identity = next; } },
    clear() { generation++; identity = ''; editors.clear(); preferences.clear(); pending = false; saving = 0; publish(); },
    startSaving() { const epoch=generation; saving++; publish(); let done=false; return () => {if(!done&&epoch===generation)saving--;done=true;publish();}; },
    read(page, key, fallback) { return preferences.get(`${identity}:${page}:${key}`) ?? fallback; },
    write(page, key, value) { if (identity) preferences.set(`${identity}:${page}:${key}`, value); },
    async request(action) {
      if (pending || saving) return false;
      pending = true; publish(); const epoch = generation;
      try {
        for (const editor of [...editors.values()]) {
          if (!editor.dirty()) continue;
          const choice = await ask(editor);
          if (epoch !== generation || choice === 'cancel') return false;
          if (choice === 'save') {
            if (!editor.save) return false;
            const result = await editor.save();
            if (epoch !== generation || result?.status !== 'success' || editor.dirty()) return false;
          } else if (choice === 'discard') editor.discard();
          else return false;
        }
        if (epoch !== generation || [...editors.values()].some(e => e.dirty())) return false;
        await action(); return true;
      } finally { if (epoch === generation) {pending = false;publish();} }
    }
  };
}

export function nextFeatureIndex(index, key, count, columns, compact = false) {
  if (key === 'Home') return 0;
  if (key === 'End') return count - 1;
  if (key === 'ArrowLeft') return Math.max(0, index - 1);
  if (key === 'ArrowRight') return Math.min(count - 1, index + 1);
  if (compact) return index;
  if (key === 'ArrowUp') return index >= columns ? index - columns : index;
  if (key === 'ArrowDown') return Math.floor(index / columns) < Math.floor((count - 1) / columns) ? Math.min(count - 1, index + columns) : index;
  return index;
}
