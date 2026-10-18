package app.bertopia

fun interface ImagePicker {
    fun pickImage(onPicked: (String?) -> Unit)
}

object NoImagePicker : ImagePicker {
    override fun pickImage(onPicked: (String?) -> Unit) = onPicked(null)
}
