const buttons = document.querySelectorAll(".category-btn");
const blocks = document.querySelectorAll(".category-block");
const searchInput = document.getElementById("searchInput");

function applyFilters() {
    const activeCategory = document.querySelector(".category-btn.active")?.dataset.category || "all";
    const keyword = (searchInput.value || "").trim().toLowerCase();

    blocks.forEach((block) => {
        const categoryMatch = activeCategory === "all" || block.dataset.category === activeCategory;
        let hasVisibleCard = false;

        const cards = block.querySelectorAll(".meme-card");
        cards.forEach((card) => {
            const title = (card.dataset.title || "").toLowerCase();
            const tags = (card.dataset.tags || "").toLowerCase();
            const keywordMatch = !keyword || title.includes(keyword) || tags.includes(keyword);
            const visible = categoryMatch && keywordMatch;
            card.classList.toggle("hidden", !visible);
            if (visible) {
                hasVisibleCard = true;
            }
        });

        block.classList.toggle("hidden", !hasVisibleCard || !categoryMatch);
    });
}

buttons.forEach((button) => {
    button.addEventListener("click", () => {
        buttons.forEach((btn) => btn.classList.remove("active"));
        button.classList.add("active");
        applyFilters();
    });
});

searchInput.addEventListener("input", applyFilters);

window.addEventListener("load", () => {
    const splash = document.getElementById("splash");
    if (!splash) return;

    setTimeout(() => {
        splash.classList.add("hide");
        setTimeout(() => splash.remove(), 500);
    }, 900);
});
