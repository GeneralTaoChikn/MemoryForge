package memoryforge.domain

/** The supported LLM generation modes.
  *
  * Each mode maps to a prompt template (see PromptTemplates) and carries some
  * default metadata (genre/style) used when the LLM omits those fields.
  */
enum GenerationMode(val key: String, val defaultGenre: String, val defaultStyle: String):
  case ReflectiveSummary
      extends GenerationMode("reflective_summary", "reflection", "reflective journal summary")
  case FunnyStory
      extends GenerationMode("funny_story", "comedy", "funny short story")
  case DramaticStory
      extends GenerationMode("dramatic_story", "drama", "dramatic short story")
  case AnimeScene
      extends GenerationMode("anime_scene", "anime", "anime-style scene")
  case BrainRot
      extends GenerationMode("brain_rot", "meme", "brain-rot meme version")
  case ThemeAnalysis
      extends GenerationMode("theme_analysis", "analysis", "recurring theme analysis")

object GenerationMode:
  /** Parse a mode key (case-insensitive). Falls back to FunnyStory. */
  def fromKey(key: String): Option[GenerationMode] =
    GenerationMode.values.find(_.key.equalsIgnoreCase(key.trim))

  val default: GenerationMode = FunnyStory
