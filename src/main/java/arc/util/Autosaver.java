/**
 * This file is part of MoreCommands. The plugin that adds a bunch of commands to your server.
 * Copyright (c) 2026  ZetaMap
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package arc.util;

import arc.Application;
import arc.ApplicationListener;
import arc.func.Cons2;


/** Save periodically the registered {@link Saveable}s and also when the application exit. */
public class Autosaver {
  /** Called when a save fail. */
  public static Cons2<Saveable, Throwable> errorHandler;

  /** Init the auto saver to run a save every seconds. */
  public static void init(Application app) {
    app.addListener(new ApplicationListener() {
      final Timekeeper rate = Timekeeper.ofSeconds(1);
      public void update() { if (rate.poll()) save(); }
      public void dispose() { forceSave(); }
    });
  }

  /** Adds to the {@code normal} priority. */
  public static void add(Saveable saveable) {
    add(saveable, SavePriority.normal);
  }

  public static synchronized void add(Saveable saveable, SavePriority priority) {
    remove(saveable);
    priority.saves = Structs.add(priority.saves, saveable);
  }

  public static synchronized void remove(Saveable saveable) {
    for (SavePriority p : SavePriority.all) {
      Saveable[] saves = Structs.remove(p.saves, saveable);
      if (saves == p.saves) continue;
      p.saves = saves;
      break;
    }
  }

  /** Don't do that! */
  public static void clear() {
    Structs.each(Autosaver::clear, SavePriority.all);
  }

  /** Don't do that! */
  public static synchronized void clear(SavePriority priority) {
    priority.saves = new Saveable[0];
  }

  public static boolean has(Saveable saveable) {
    return priorityOf(saveable) != null;
  }

  public static boolean has(Saveable saveable, SavePriority priority) {
    return Structs.contains(priority.saves, saveable);
  }

  public static SavePriority priorityOf(Saveable saveable) {
    return Structs.find(SavePriority.all, p -> has(saveable, p));
  }

  public static boolean saveNeeded() {
    return Structs.contains(SavePriority.all, p -> Structs.contains(p.saves, Saveable::modified));
  }

  /** Save all registered things now, only if modified. */
  public static boolean save() {
    boolean needed = saveNeeded();
    if (needed) saveAll(false);
    return needed;
  }

  /** Save all registered things now, even not modified. */
  public static void forceSave() {
    saveAll(true);
  }

  private static void saveAll(boolean force) {
    for (SavePriority p : SavePriority.all) {
      for (Saveable s : p.saves) {
        if (!force && !s.modified()) continue;
        try {
          if (force) s.forceSave();
          else s.save();
        } catch (Throwable t) {
          Log.err("Failed to save " + s.name(), t);
          if (errorHandler != null) errorHandler.get(s, t);
        }
      }
    }
  }


  /** Defines a things that can be saved by the {@link Autosaver}. */
  public interface Saveable {
    /** Used for logging. */
    String name();
    boolean modified();
    void save();
    default void forceSave() { save(); }
  }


  /** Defines the order to save things. */
  public enum SavePriority {
    /** Highest save priority. Commonly the things that manages settings. */
    high,
    /** Default save priority. Commonly for settings things. */
    normal,
    /** Lowest save priority, for things that should be saved last. */
    low;

    static final SavePriority[] all = values();
    // More simple to store the saveable things here.
    // Because the priority should not be modified after registration.
    volatile Saveable[] saves = {};
  }
}
