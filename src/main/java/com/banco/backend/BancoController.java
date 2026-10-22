package com.banco.backend;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.HashMap;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;

@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*") 
@RequestMapping("/api")
public class BancoController {

    @Autowired
    private MongoTemplate mongoTemplate;

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> credenciales) {
        String rfc = credenciales.get("rfc").trim();
        BasicQuery query = new BasicQuery("{\"_id\": {\"$regex\": \"^" + rfc + "$\", \"$options\": \"i\"}}");
        Map usuario = mongoTemplate.findOne(query, Map.class, "usuarios");

        if (usuario != null) {
            String rfcReal = usuario.get("_id").toString();
            String rol = "CLIENTE";
            if (usuario.get("rol") != null) {
                rol = usuario.get("rol").toString().toUpperCase();
            }
            return Map.of("exito", true, "mensaje", "Login correcto", "rfc", rfcReal, "rol", rol);
        } else {
            return Map.of("exito", false, "mensaje", "RFC no encontrado en la base de datos");
        }
    }

    @GetMapping("/usuario/{rfc}")
    public Map<String, Object> obtenerUsuario(@PathVariable String rfc) {
        BasicQuery query = new BasicQuery("{\"_id\": {\"$regex\": \"^" + rfc.trim() + "$\", \"$options\": \"i\"}}");
        Map usuario = mongoTemplate.findOne(query, Map.class, "usuarios");

        if (usuario != null) {
            String nombre = usuario.get("nombre").toString();
            double saldoReal = 0.0;
            try {
                if (usuario.get("cuentas") instanceof List) {
                    List<?> cuentas = (List<?>) usuario.get("cuentas");
                    if (!cuentas.isEmpty() && cuentas.get(0) instanceof Map) {
                        Map<?, ?> cuenta = (Map<?, ?>) cuentas.get(0);
                        if (cuenta.get("saldo") != null) {
                            saldoReal = Double.parseDouble(cuenta.get("saldo").toString());
                        }
                    }
                }
            } catch (Exception e) {}
            return Map.of("exito", true, "nombre", nombre, "saldo", saldoReal);
        } else {
            return Map.of("exito", false, "mensaje", "Usuario no encontrado");
        }
    }

    // --- NUEVO: OBTENER TODOS LOS USUARIOS PARA EL ADMIN ---
    @GetMapping("/usuarios")
    public Map<String, Object> obtenerTodosUsuarios() {
        try {
            List<Map> usuarios = mongoTemplate.findAll(Map.class, "usuarios");
            return Map.of("exito", true, "usuarios", usuarios);
        } catch (Exception e) {
            return Map.of("exito", false, "mensaje", "Error al obtener usuarios: " + e.getMessage());
        }
    }

    @PostMapping("/retirar")
    public Map<String, Object> realizarRetiro(@RequestBody Map<String, Object> datos) {
        try {
            if (datos == null || datos.get("rfc") == null || datos.get("monto") == null) {
                return Map.of("exito", false, "mensaje", "Faltan datos de RFC o monto");
            }
            String rfc = datos.get("rfc").toString().trim();
            double montoRetiro = Double.parseDouble(datos.get("monto").toString());

            BasicQuery buscarUsuario = new BasicQuery("{\"_id\": {\"$regex\": \"^" + rfc + "$\", \"$options\": \"i\"}}");
            Map usuario = mongoTemplate.findOne(buscarUsuario, Map.class, "usuarios");
            if (usuario == null) return Map.of("exito", false, "mensaje", "Usuario no encontrado");

            double saldoActual = 0.0;
            Object noCuenta = null;

            if (usuario.get("cuentas") instanceof List) {
                List<?> cuentas = (List<?>) usuario.get("cuentas");
                if (!cuentas.isEmpty() && cuentas.get(0) instanceof Map) {
                    Map<?, ?> cuenta = (Map<?, ?>) cuentas.get(0);
                    if (cuenta.get("saldo") != null) saldoActual = Double.parseDouble(cuenta.get("saldo").toString());
                    if (cuenta.get("no_cuenta") != null) noCuenta = cuenta.get("no_cuenta");
                }
            }

            if (saldoActual < montoRetiro) return Map.of("exito", false, "mensaje", "Saldo insuficiente");

            double nuevoSaldo = saldoActual - montoRetiro;
            Update actualizarSaldo = new Update().set("cuentas.0.saldo", nuevoSaldo);
            mongoTemplate.updateFirst(buscarUsuario, actualizarSaldo, "usuarios");

            Map<String, Object> nuevoMovimiento = new HashMap<>();
            nuevoMovimiento.put("tipo", "R");
            nuevoMovimiento.put("monto", montoRetiro);
            nuevoMovimiento.put("fecha", new Date());
            nuevoMovimiento.put("rfc", rfc);
            nuevoMovimiento.put("clve_sucursal", 1);
            if (noCuenta != null) nuevoMovimiento.put("no_cuenta", noCuenta);
            mongoTemplate.save(nuevoMovimiento, "movimientos");

            return Map.of("exito", true, "mensaje", "Retiro exitoso", "saldoRestante", nuevoSaldo);
        } catch (Exception e) {
            return Map.of("exito", false, "mensaje", "Error en servidor: " + e.toString());
        }
    }

    @PostMapping("/depositar")
    public Map<String, Object> realizarDeposito(@RequestBody Map<String, Object> datos) {
        try {
            if (datos == null || datos.get("rfc") == null || datos.get("monto") == null) {
                return Map.of("exito", false, "mensaje", "Faltan datos de RFC o monto");
            }
            String rfc = datos.get("rfc").toString().trim();
            double montoDeposito = Double.parseDouble(datos.get("monto").toString());

            BasicQuery buscarUsuario = new BasicQuery("{\"_id\": {\"$regex\": \"^" + rfc + "$\", \"$options\": \"i\"}}");
            Map usuario = mongoTemplate.findOne(buscarUsuario, Map.class, "usuarios");
            if (usuario == null) return Map.of("exito", false, "mensaje", "Usuario no encontrado");

            double saldoActual = 0.0;
            Object noCuenta = null;

            if (usuario.get("cuentas") instanceof List) {
                List<?> cuentas = (List<?>) usuario.get("cuentas");
                if (!cuentas.isEmpty() && cuentas.get(0) instanceof Map) {
                    Map<?, ?> cuenta = (Map<?, ?>) cuentas.get(0);
                    if (cuenta.get("saldo") != null) saldoActual = Double.parseDouble(cuenta.get("saldo").toString());
                    if (cuenta.get("no_cuenta") != null) noCuenta = cuenta.get("no_cuenta");
                }
            }

            double nuevoSaldo = saldoActual + montoDeposito;
            Update actualizarSaldo = new Update().set("cuentas.0.saldo", nuevoSaldo);
            mongoTemplate.updateFirst(buscarUsuario, actualizarSaldo, "usuarios");

            Map<String, Object> nuevoMovimiento = new HashMap<>();
            nuevoMovimiento.put("tipo", "D");
            nuevoMovimiento.put("monto", montoDeposito);
            nuevoMovimiento.put("fecha", new Date());
            nuevoMovimiento.put("rfc", rfc);
            nuevoMovimiento.put("clve_sucursal", 1);
            if (noCuenta != null) nuevoMovimiento.put("no_cuenta", noCuenta);
            mongoTemplate.save(nuevoMovimiento, "movimientos");

            return Map.of("exito", true, "mensaje", "Depósito exitoso", "saldoRestante", nuevoSaldo);
        } catch (Exception e) {
            return Map.of("exito", false, "mensaje", "Error en servidor: " + e.toString());
        }
    }

    @PostMapping("/crear")
    public Map<String, Object> crearCuenta(@RequestBody Map<String, Object> datos) {
        try {
            String rfc = datos.get("rfc").toString().trim().toUpperCase();
            BasicQuery buscarUsuario = new BasicQuery("{\"_id\": {\"$regex\": \"^" + rfc + "$\", \"$options\": \"i\"}}");
            Map existe = mongoTemplate.findOne(buscarUsuario, Map.class, "usuarios");
            if (existe != null) return Map.of("exito", false, "mensaje", "Este RFC ya tiene una cuenta.");

            String nombre = datos.getOrDefault("nombre", "").toString();
            String apellidos = datos.getOrDefault("apellidos", "").toString();
            String calleNum = datos.getOrDefault("calle_num", "").toString();
            String cp = datos.getOrDefault("cp", "").toString();
            String colonia = datos.getOrDefault("colonia", "").toString();
            String municipio = datos.getOrDefault("municipio", "").toString();
            String telefono = datos.getOrDefault("telefono", "").toString();
            String tipo = datos.getOrDefault("tipo", "F").toString();
            double saldoInicial = Double.parseDouble(datos.get("saldo").toString());

            int numeroCuentaGenerado = (int) (Math.random() * 90000000) + 10000000;
            Map<String, Object> nuevaCuenta = new HashMap<>();
            nuevaCuenta.put("saldo", saldoInicial);
            nuevaCuenta.put("no_cuenta", numeroCuentaGenerado);

            List<Map<String, Object>> listaCuentas = new ArrayList<>();
            listaCuentas.add(nuevaCuenta);

            Map<String, Object> nuevoUsuario = new HashMap<>();
            nuevoUsuario.put("_id", rfc);
            nuevoUsuario.put("nombre", nombre);
            nuevoUsuario.put("apellidos", apellidos);
            nuevoUsuario.put("calle_num", calleNum);
            nuevoUsuario.put("cp", cp);
            nuevoUsuario.put("colonia", colonia);
            nuevoUsuario.put("municipio", municipio);
            nuevoUsuario.put("telefono", telefono);
            nuevoUsuario.put("tipo", tipo);
            nuevoUsuario.put("rol", "CLIENTE"); // Por defecto todos nacen como clientes
            nuevoUsuario.put("cuentas", listaCuentas);

            mongoTemplate.save(nuevoUsuario, "usuarios");

            Map<String, Object> nuevoMovimiento = new HashMap<>();
            nuevoMovimiento.put("tipo", "D");
            nuevoMovimiento.put("monto", saldoInicial);
            nuevoMovimiento.put("fecha", new Date());
            nuevoMovimiento.put("rfc", rfc);
            nuevoMovimiento.put("clve_sucursal", 1);
            nuevoMovimiento.put("no_cuenta", numeroCuentaGenerado);
            mongoTemplate.save(nuevoMovimiento, "movimientos");

            return Map.of("exito", true, "mensaje", "Cuenta creada exitosamente");
        } catch (Exception e) {
            return Map.of("exito", false, "mensaje", "Error al crear cuenta: " + e.getMessage());
        }
    }

    @PostMapping("/eliminar")
    public Map<String, Object> eliminarCuenta(@RequestBody Map<String, String> datos) {
        try {
            String rfc = datos.get("rfc").trim();
            BasicQuery query = new BasicQuery("{\"_id\": {\"$regex\": \"^" + rfc + "$\", \"$options\": \"i\"}}");
            mongoTemplate.remove(query, "usuarios");
            return Map.of("exito", true, "mensaje", "Cuenta eliminada correctamente");
        } catch (Exception e) {
            return Map.of("exito", false, "mensaje", "Error al eliminar: " + e.getMessage());
        }
    }
}