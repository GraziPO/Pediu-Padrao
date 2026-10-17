package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.repository.ClienteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ClienteService {

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private AuditoriaService auditoriaService;

    public Cliente novoCliente(Cliente cliente) {
        return clienteRepository.save(cliente);
    }

    public List<Cliente> listarClientes() {
        return clienteRepository.findAll();
    }

    // Método para excluir cliente
    public void excluir(String id) {
        Optional<Cliente> anterior = clienteRepository.findById(id);
        clienteRepository.deleteById(id);
        anterior.ifPresent(c -> {
            Map<String, Object> depois = new LinkedHashMap<>();
            campos(c).keySet().forEach(campo -> depois.put(campo, null));
            auditoriaService.registrar("EXCLUSÃO DE CLIENTE", "Cliente", id, campos(c), depois, null);
        });
    }

    public Optional<Cliente> buscarPorId(String id) {
        return clienteRepository.findById(id);
    }

    // Método para salvar um novo cliente ou atualizar um cliente existente
    public Cliente salvar(Cliente cliente) {
        // Se o cliente não tem ID (novo cliente), salva como novo
        if (cliente.getId() == null) {
            Cliente salvo = clienteRepository.save(cliente);  // Cria um novo cliente
            auditoriaService.registrar("INCLUSÃO DE CLIENTE", "Cliente", salvo.getId(), null, campos(salvo), null);
            return salvo;
        } // Se já tem ID (cliente existente), atualiza
        else {
            // Verifica se o cliente existe antes de atualizar
            Optional<Cliente> anterior = clienteRepository.findById(cliente.getId());
            if (anterior.isPresent()) {
                Map<String, Object> antes = campos(anterior.get());
                Cliente salvo = clienteRepository.save(cliente);  // Atualiza o cliente existente
                auditoriaService.registrar("ALTERAÇÃO DE CLIENTE", "Cliente", salvo.getId(), antes, campos(salvo), null);
                return salvo;
            } else {
                throw new IllegalArgumentException("Cliente não encontrado para atualização.");
            }
        }
    }

    /** Campos cadastrais auditados (endereco e telefone principalmente). */
    private Map<String, Object> campos(Cliente c) {
        Map<String, Object> campos = new LinkedHashMap<>();
        campos.put("nome", c.getNome());
        campos.put("telefone", c.getTelefone());
        campos.put("cep", c.getCep());
        campos.put("logradouro", c.getLogradouro());
        campos.put("numero", c.getNumero());
        campos.put("complemento", c.getComplemento());
        campos.put("bairro", c.getBairro());
        campos.put("cidade", c.getCidade());
        campos.put("estado", c.getEstado());
        campos.put("endereco", c.getEndereco());
        return campos;
    }

}
