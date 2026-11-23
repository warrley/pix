# frozen_string_literal: true

# Seeds idempotentes para inicialização do ambiente de desenvolvimento e testes (Postman / Apresentação)
puts "== Semeando Banco de Dados para Desenvolvimento & Demonstração =="

ActiveRecord::Base.transaction do
  # 1. Usuária Alice (Cliente / Operadora principal de testes)
  alice = User.find_by(email: "alice.test@example.com") || User.find_by(email: "husky.test@example.com")
  alice ||= User.new
  alice.assign_attributes(
    name: "Alice Silva",
    email: "alice.test@example.com",
    doc_id: "68072551019", # CPF válido com dígitos verificadores corretos
    phone: "+5585999990001",
    password: "password123",
    role: "operator" # Role operator permite acesso completo a todos os escopos e endpoints de segurança
  )
  alice.save!(validate: false)

  # 2. Usuário Bob (Recebedor para testes de transferências e estorno)
  bob = User.find_or_initialize_by(email: "bob.test@example.com")
  bob.assign_attributes(
    name: "Bob Santos",
    doc_id: "33734063078", # CPF válido com dígitos verificadores corretos
    phone: "+5585999990002",
    password: "password123",
    role: "customer"
  )
  bob.save!(validate: false)

  # 3. Conta Bancária da Alice (ID fixo de desenvolvimento ou primeira conta)
  acc_alice = Account.find_or_initialize_by(user: alice)
  acc_alice.assign_attributes(
    account_number: "10001-1",
    agency_number: "0001",
    balance: 1500.0,
    status: "active",
    fraud_blocked: false
  )
  acc_alice.save!

  # 4. Conta Bancária do Bob
  acc_bob = Account.find_or_initialize_by(user: bob)
  acc_bob.assign_attributes(
    account_number: "20002-2",
    agency_number: "0001",
    balance: 800.0,
    status: "active",
    fraud_blocked: false
  )
  acc_bob.save!

  # 5. Chaves Pix
  PixKey.find_or_create_by!(account: acc_bob, key_value: "bob.test@example.com") do |k|
    k.key_type = "email"
    k.status = "active"
  end

  pix_key_alice = PixKey.find_by(account: acc_alice, key_value: "husky.test@example.com")
  pix_key_alice ||= PixKey.find_by(account: acc_alice, key_value: "alice.test@example.com")
  pix_key_alice ||= PixKey.new(account: acc_alice)
  pix_key_alice.assign_attributes(
    account: acc_alice,
    key_value: "alice.test@example.com",
    key_type: "email",
    status: "active"
  )
  pix_key_alice.save!

  # 6. Movimentações de demonstração
  demo_transactions = [
    { days_ago: 6, source: acc_bob, destination: acc_alice, amount_cents: 12500, status: "completed", description: "Pagamento de serviço" },
    { days_ago: 5, source: acc_alice, destination: acc_bob, amount_cents: 8750, status: "completed", description: "Reembolso de almoço" },
    { days_ago: 4, source: acc_bob, destination: acc_alice, amount_cents: 42000, status: "completed", description: "Venda de produto" },
    { days_ago: 3, source: acc_alice, destination: acc_bob, amount_cents: 25000, status: "refunded", description: "Compra cancelada" },
    { days_ago: 2, source: acc_bob, destination: acc_alice, amount_cents: 18990, status: "completed", description: "Assinatura mensal" },
    { days_ago: 1, source: acc_alice, destination: acc_bob, amount_cents: 500000, status: "failed", description: "Transferência recusada", failure_reason: "Saldo insuficiente" },
    { days_ago: 0, source: acc_bob, destination: acc_alice, amount_cents: 7500, status: "completed", description: "Pix recebido" },
    { days_ago: 0, source: acc_alice, destination: acc_bob, amount_cents: 3200, status: "cancelled", description: "Pix cancelado pelo usuário" }
  ]

  demo_transactions.each_with_index do |demo, index|
    end_to_end_id = format("E01011010%020d%03d", 202611230000 + index, index + 1)
    transaction = Transaction.find_or_initialize_by(end_to_end_id: end_to_end_id)
    timestamp = demo[:days_ago].days.ago.change(hour: 9 + index, min: 15)
    transaction.assign_attributes(
      source_account: demo[:source],
      destination_account: demo[:destination],
      pix_key_used: demo[:destination] == acc_alice ? "alice.test@example.com" : "bob.test@example.com",
      amount_cents: demo[:amount_cents],
      description: demo[:description],
      status: demo[:status],
      failure_reason: demo[:failure_reason],
      created_at: timestamp,
      updated_at: timestamp
    )
    transaction.save!
  end

  # 7. Emissão de Tokens Pré-configurados para a Collection do Postman
  scopes = ApiAccessToken::OPERATOR_SCOPES
  raw_token, _ = ApiAccessToken.issue!(user: alice, scopes: scopes)

  puts "\n[OK] Banco semeado com sucesso!"
  puts " - Alice: alice.test@example.com (Senha: password123)"
  puts "   Conta ID: #{acc_alice.id} | Número: #{acc_alice.account_number} | Saldo: R$ #{acc_alice.balance}"
  puts " - Bob:   bob.test@example.com (Chave Pix: bob.test@example.com)"
  puts "   Conta ID: #{acc_bob.id} | Número: #{acc_bob.account_number} | Saldo: R$ #{acc_bob.balance}"
  puts "\nToken gerado para a Alice: #{raw_token}"
end
