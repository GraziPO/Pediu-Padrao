/*
 * Comportamento da sidebar (carregado pelo layout.html).
 * - toggleSubmenu: abre/fecha o submenu de Configurações (chamado no onclick do sidebar.html)
 * - destaca o item da página atual e abre o submenu quando a página está dentro dele
 * - em telas pequenas, o botão ☰ mostra/esconde a sidebar
 */

function toggleSubmenu(event, link) {
    event.preventDefault();
    link.closest('.nav-item').classList.toggle('open');
}

document.addEventListener('DOMContentLoaded', function () {
    var sidebar = document.getElementById('sidebar');
    if (!sidebar) {
        return;
    }

    // Item ativo: o link cujo caminho é o início da URL atual (o mais específico vence)
    var caminho = window.location.pathname;
    var melhor = null;
    sidebar.querySelectorAll('a.nav-link[href]').forEach(function (a) {
        var href = a.getAttribute('href');
        if (!href || href === '#') {
            return;
        }
        var corresponde = href === '/' ? caminho === '/' : (caminho === href || caminho.indexOf(href + '/') === 0);
        if (corresponde && (!melhor || href.length > melhor.getAttribute('href').length)) {
            melhor = a;
        }
    });
    if (melhor) {
        melhor.classList.add('active');
        var submenu = melhor.closest('.submenu');
        if (submenu) {
            submenu.closest('.nav-item').classList.add('open');
        }
    }

    // Telas pequenas: botão ☰
    var botao = document.querySelector('.toggle-btn');
    if (botao) {
        botao.addEventListener('click', function () {
            sidebar.classList.toggle('aberta');
        });
    }
});
